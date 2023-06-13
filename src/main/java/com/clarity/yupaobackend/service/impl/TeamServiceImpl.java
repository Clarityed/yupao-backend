package com.clarity.yupaobackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.clarity.yupaobackend.common.ErrorCode;
import com.clarity.yupaobackend.exception.BusinessException;
import com.clarity.yupaobackend.model.domain.Team;
import com.clarity.yupaobackend.mapper.TeamMapper;
import com.clarity.yupaobackend.model.domain.User;
import com.clarity.yupaobackend.model.domain.UserTeam;
import com.clarity.yupaobackend.model.dto.TeamQuery;
import com.clarity.yupaobackend.model.enums.TeamStatusEnum;
import com.clarity.yupaobackend.model.request.TeamDeleteRequest;
import com.clarity.yupaobackend.model.request.TeamJoinRequest;
import com.clarity.yupaobackend.model.request.TeamQuitRequest;
import com.clarity.yupaobackend.model.request.TeamUpdateRequest;
import com.clarity.yupaobackend.model.vo.TeamUserVO;
import com.clarity.yupaobackend.model.vo.UserVO;
import com.clarity.yupaobackend.service.TeamService;
import com.clarity.yupaobackend.service.UserService;
import com.clarity.yupaobackend.service.UserTeamService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* @author Clarity
* @description 针对表【team(队伍)】的数据库操作Service实现
* @createDate 2022-09-30 10:05:00
*/
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
    implements TeamService{

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Override
    // 该方法开启事务，要么都成功提交，要么都失败
    @Transactional(rollbackFor = Exception.class)
    public long addTeam(Team team, User loginUser) {
        // 1. 请求参数是否为空
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // 2. 是否登录，未登录不允许创建
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录不允许创建队伍");
        }
        // final 在这里的作用逃过编译器的检查，距离太远才使用
        final long userId = loginUser.getId();
        // 3 校验信息
        //  3.1 队伍人数 > 1，且 <= 20
        int maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍人数不符合要求");
        }
        //  3.2 队伍标题 <= 20
        String teamName = team.getName();
        if (StringUtils.isBlank(teamName) || teamName.length() > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍标题不符合要求");
        }
        /*if (teamName.length() == 0 || teamName.length() > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍标题不符合要求");
        }*/
        //  3.3 描述 <= 512
        String teamDescription = team.getDescription();
        if (StringUtils.isNotBlank(teamDescription) && teamDescription.length() > 512) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "描述不符合要求");
        }
        //  3.4 status 是否公开（int）不传默认为 0 （公开），这里我们去定义一个枚举类来进行判断，当前队伍的状态
        Integer teamStatus = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(teamStatus);
        if (teamStatusEnum == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍状态不符合要求");
        }
        //  3.5 如果 status 加密状态，一定要有密码，且密码 <= 32
        String teamPassword = team.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(teamPassword) || teamPassword.length() > 32) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不符合要求");
            }
        }
        //  3.6 超时时间 > 当前时间
        Date teamExpireTime = team.getExpireTime();
        if (new Date().after(teamExpireTime)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍过期时间在当前时间之前");
        }
        //  3.7 校验用户最多创建 5 个队伍
        // todo 有 bug，可能同时创建 100 个队伍，可以用锁来解决
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        long hasTeamNum = this.count(queryWrapper);
        if (hasTeamNum >= 5) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍创建数量已达当前系统支持的最大值");
        }
        // 4. 插入队伍信息到队伍表
        team.setId(null);
        team.setUserId(userId);
        team.setCreateTime(new Date());
        team.setUpdateTime(new Date());
        int result = teamMapper.insert(team);
        Long teamId = team.getId();
        if (result == 0 || teamId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "队伍插入失败");
        }
        // 5. 插入用户到队伍关系表
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        userTeam.setCreateTime(new Date());
        userTeam.setUpdateTime(new Date());
        boolean userTeamResult = userTeamService.save(userTeam);
        if (!userTeamResult) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "创建队伍失败");
        }
        return teamId;
    }

    @Override
    public List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdmin) {
        // 1. 从请求参数中取出队伍名称等条件，如果存在则作为查询条件。
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        if (teamQuery != null) {
            Long id = teamQuery.getId();
            if (id != null && id > 0) {
                queryWrapper.eq("id", id);
            }
            // 查询当前用户已加入的队伍判断
            List<Long> idList = teamQuery.getIdList();
            if (CollectionUtils.isNotEmpty(idList)) {
                queryWrapper.in("id", idList);
            }
            // 3. 可以通过某个关键词同时对名称和描述查询
            String searchText = teamQuery.getSearchText();
            if (StringUtils.isNotBlank(searchText)) {
                queryWrapper.and(qw -> qw.like("name", searchText).or().like("description", searchText));
            }
            String name = teamQuery.getName();
            // 判断字符串存在，且不能为空字符串
            if (StringUtils.isNotBlank(name)) {
                queryWrapper.like("name", name);
            }
            String description = teamQuery.getDescription();
            if (StringUtils.isNotBlank(description)) {
                queryWrapper.like("description", description);
            }
            Integer maxNum = teamQuery.getMaxNum();
            // 查询最大人数
            if (maxNum != null && maxNum > 0) {
                queryWrapper.eq("maxNum", maxNum);
            }
            // 根据创建人来查询队伍
            Long userId = teamQuery.getUserId();
            if (userId != null && userId > 0) {
                queryWrapper.eq("userId", userId);
            }
            // 根据队伍状态查询队伍
            Integer status = teamQuery.getStatus();
            TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
            if (teamStatusEnum == null) {
                teamStatusEnum = TeamStatusEnum.PUBLIC;
            }
            // 4. 只有管理员才能查看加密还有非公开的房间
            // 发现问题：普通用户无法查看加密队伍，现在改为普通用户只有私密队伍无法查看
            if (!isAdmin && teamStatusEnum.equals(TeamStatusEnum.PRIVATE)) {
                throw new BusinessException(ErrorCode.NO_AUTH);
            }
            queryWrapper.eq("status", teamStatusEnum.getValue());
        }
        // 2. 不展示已过期的队伍，如果队伍未设置过期时间也是能查询出来的
        // 下面这行代码的意思就是，查询队伍过期时间大于当前时间或者队伍没有设置过期时间，也就算永久保留的队伍
        queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));
        List<Team> teamList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }
        // 5. 我们这里使用关联查询创建人，写 SQL 自己有时间了自己实现
        // 如果有多张表建议还是使用 写 SQL 来来查询，因为关联查询多张表，然后数据量有大，性能会很差
        List<TeamUserVO> teamUserVOList = new ArrayList<>();
        for (Team team : teamList) {
            Long userId = team.getUserId();
            if (userId == null) {
                continue;
            }
            // 得到创建人用户的信息
            User user = userService.getById(userId);
            // 用于存放要传给前端的队伍信息
            TeamUserVO teamUserVO = new TeamUserVO();
            BeanUtils.copyProperties(team, teamUserVO);
            // 用户信息脱敏，也就是复制用户信息到封装类中，与上面的一样
            // 有可能这个用户信息是不存在的
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                teamUserVO.setCreateUser(userVO);
            }
            teamUserVOList.add(teamUserVO);
        }

        return teamUserVOList;
    }

    @Override
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser) {
        // 1. 判断请求参数是否为空
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // 2. 查询队伍是否存在
        Long id = teamUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Team oldTeam = teamMapper.selectById(id);
        if (oldTeam == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        // 3. 只有管理员或者队伍的创建者可以修改
        if (oldTeam.getId() != loginUser.getId() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        // 5. 如果队伍状态改为加密，必须要有密码
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(teamUpdateRequest.getPassword())) {
                throw new BusinessException(ErrorCode.NULL_ERROR, "加密类型队伍，密码不能为空");
            }
        }
        // 6. 更新队伍成功
        Team newTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest, newTeam);
        return this.updateById(newTeam);
    }

    /**
     * 根据队伍 id 获得该队伍
     * @param teamId 队伍 id
     * @return 队伍
     */
    private Team getTeamById(Long teamId) {
        // 队伍 id 不能为空，或者小于等于 0
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // 2.1 队伍必须存在
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "队伍不存在");
        }
        return team;
    }

    @Override
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser) {
        // 老样子判断对象是否为空
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Long teamId = teamJoinRequest.getTeamId();
        Team team = this.getTeamById(teamId);
        // 2.2 未过期的队伍
        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍已过期");
        }
        // 4. 禁止加入私有队伍
        Integer teamStatus = team.getStatus();
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(teamStatus);
        if (TeamStatusEnum.PRIVATE.equals(teamStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "禁止加入私有队伍");
        }
        // 5. 如果加入的队伍是加密的，必须密码匹配才可以
        String password = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || !password.equals(team.getPassword())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "密码错误");
            }
        }
        // 使用 Redisson 获得一把锁
        RLock lock = redissonClient.getLock("yupao:join_team:lock");
        // 只有一个线程能获得到锁
        try {
            while (true) {
                // tryLock 的第二个参数如果填写 -1 表示，启动续锁功能
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                    System.out.println("getLock: " + Thread.currentThread().getId());
                    // 1. 用户最多加入 5 个队伍
                    long userId = loginUser.getId();
                    QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", userId);
                    long hasJoinNum = userTeamService.count(userTeamQueryWrapper);
                    if (hasJoinNum >= 5) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR, "最多创建和加入 5 个队伍");
                    }
                    // 2.3 只能加入未满队伍
                    userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("teamId", teamId);
                    long teamHasJoinNum = userTeamService.count(userTeamQueryWrapper);
                    if (teamHasJoinNum >= team.getMaxNum()) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR, "队伍人数已满");
                    }
                    // 3. 不能重复加入已加入的队伍
                    userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("teamId", teamId);
                    userTeamQueryWrapper.eq("userId", userId);
                    long hasJoinTeam = userTeamService.count(userTeamQueryWrapper);
                    if (hasJoinTeam > 0) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR, "已加入该队伍");
                    }
                    // 6. 新增 队伍-用户 关联信息
                    UserTeam userTeam = new UserTeam();
                    userTeam.setUserId(userId);
                    userTeam.setTeamId(teamId);
                    userTeam.setJoinTime(new Date());
                    userTeam.setCreateTime(new Date());
                    userTeam.setUpdateTime(new Date());
                    return userTeamService.save(userTeam);
                }
            }
        } catch (InterruptedException e) {
            log.error("doJoinTeam: ", e);
            return false;
        } finally {
            // 注意一定要在 finally 里写释放锁的语句，因为这样程序就算在 try 里面出现异常报错，也能够正常释放锁，不会造成死锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
        // 2. 校验请求参数
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Long teamId = teamQuitRequest.getTeamId();
        Team team = this.getTeamById(teamId);
        // 4. 校验我是否已经加入队伍
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        long userId = loginUser.getId();
        queryWrapper.eq("teamId", teamId);
        queryWrapper.eq("userId", userId);
        long count = userTeamService.count(queryWrapper);
        if (count == 0) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "您还没加入该队伍");
        }
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", teamId);
        long teamHasJoinNum = userTeamService.count(queryWrapper);
        // 5.1 如果队伍只剩下一个人
        if (teamHasJoinNum == 1) {
            // 解散队伍，包括删除队伍信息，和队伍加入记录
            int teamDeleteById = teamMapper.deleteById(teamId);
            if (teamDeleteById == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "队伍删除失败");
            }
            boolean userTeamRemoveById = userTeamService.remove(queryWrapper);
            if (!userTeamRemoveById) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "队伍关系删除失败");
            }
        } else { // 还有其他人的情况下
            // 如果是队长退出队伍，权限转移给第二个早加入的用户 ——先来后到
            if (userId == team.getUserId()) {
                // 查询已加入队伍的所有用户和加入时间，我们这里使用 id 来判断谁先加入队伍
                queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("teamId", teamId);
                queryWrapper.last("order by id limit 2");
                List<UserTeam> userTeamList = userTeamService.list(queryWrapper);
                if (userTeamList == null || userTeamList.size() <= 1) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
                // 更新队伍队长 id
                UserTeam nextUserTeam = userTeamList.get(1);
                Long nextTeamLeaderId = nextUserTeam.getUserId();
                Team updateTeam = new Team();
                updateTeam.setId(teamId);
                updateTeam.setUserId(nextTeamLeaderId);
                int result = teamMapper.updateById(updateTeam);
                if (result == 0) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "队长更新失败");
                }
                // 删除前任队长信息
                boolean removeUserTeam = this.deleteOldTeamMasterInfo(teamId, userId);
                if (!removeUserTeam) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除前任队长关系记录失败");
                }
            } else {
                boolean removeUserTeam = this.deleteOldTeamMasterInfo(teamId, userId);
                if (!removeUserTeam) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除前任队长关系记录失败");
                }
            }
        }
        return true;
    }

    /**
     * 删除老队长的队伍关系记录
     * @param teamId 队伍 id
     * @param userId 用户 id
     * @return 1 -删除成功 0- 删除失败
     */
    private boolean deleteOldTeamMasterInfo(Long teamId, Long userId) {
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        userTeamQueryWrapper.eq("userId", userId);
        return userTeamService.remove(userTeamQueryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(TeamDeleteRequest teamDeleteRequest, User loginUser) {
        // 2. 校验请求参数
        if (teamDeleteRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // 3. 校验队伍是否存在
        Long teamId = teamDeleteRequest.getTeamId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "队伍不存在");
        }
        // 4. 校验你是不是队伍的队长
        long userId = loginUser.getId();
        if (userId != team.getUserId()) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权限进行此操作");
        }
        // 5. 移除所有加入队伍的关联信息
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", teamId);
        boolean userTeamResult = userTeamService.remove(queryWrapper);
        if (!userTeamResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍关系失败");
        }
        boolean teamResult = this.removeById(teamId);
        if (!teamResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除队伍失败");
        }
        return true;
    }

    @Override
    public List<TeamUserVO> isUserJoinTeam(List<TeamUserVO> teamList, HttpServletRequest request) {
        // 1. 获取队伍 id 集合
        List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        try {
            // getLoginUser 里面设置了未登录抛出异常终止操作，如果想要未登录也能查看队伍，那么就要捕获异常
            User loginUser = userService.getLoginUser(request);
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.in("teamId", teamIdList);
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            // 已加入队伍的 id 集合,我们可以肯定队伍 id 唯一
            Set<Long> hasJoinTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
            teamList.forEach(team -> {
                boolean hasJoin = hasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        QueryWrapper<UserTeam> userJoinTeamQueryWrapper = new QueryWrapper<>();
        userJoinTeamQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userJoinTeamQueryWrapper);
        // 得出队伍人数
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamList.forEach(team -> team.setHasJoinNumber(teamIdUserTeamList.getOrDefault(team.getId(), new ArrayList<>()).size()));
        return teamList;
    }
}




