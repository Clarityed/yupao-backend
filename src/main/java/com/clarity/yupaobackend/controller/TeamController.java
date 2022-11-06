package com.clarity.yupaobackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clarity.yupaobackend.common.BaseResponse;
import com.clarity.yupaobackend.common.ErrorCode;
import com.clarity.yupaobackend.exception.BusinessException;
import com.clarity.yupaobackend.model.domain.Team;
import com.clarity.yupaobackend.model.domain.User;
import com.clarity.yupaobackend.model.domain.UserTeam;
import com.clarity.yupaobackend.model.dto.TeamQuery;
import com.clarity.yupaobackend.model.request.*;
import com.clarity.yupaobackend.model.vo.TeamUserVO;
import com.clarity.yupaobackend.service.TeamService;
import com.clarity.yupaobackend.service.UserService;
import com.clarity.yupaobackend.service.UserTeamService;
import com.clarity.yupaobackend.utils.ResultUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 队伍接口
 *
 * @author: clarity
 * @date: 2022年09月30日 10:17
 */
@RestController
@RequestMapping("/team")
@CrossOrigin(origins = "http://localhost:5173/", allowCredentials = "true")
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    // 这里就不采用 RestFul 风格的代码形式了，没有太大差别，定义自己的一套规范就可以了
    // 或者在自己公司里，按公司的要求就行，没有一个必须的规范要求

    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request) {
        if (teamAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest, team);
        long teamId = teamService.addTeam(team, loginUser);
        return ResultUtils.success(teamId);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody TeamDeleteRequest teamDeleteRequest, HttpServletRequest request) {
        if (teamDeleteRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(teamDeleteRequest, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败");
        }
        return ResultUtils.success(true);
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest, HttpServletRequest request) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return ResultUtils.success(team);
    }

    // 这个接口未登录也能使用
    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        boolean isAdmin = userService.isAdmin(request);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin);
        List<TeamUserVO> hasJoinSignTeamList = teamService.isUserJoinTeam(teamList, request);
        return ResultUtils.success(hasJoinSignTeamList);
    }

    // todo 队伍列表展示分页
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsPage(TeamQuery teamQuery) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Team team = new Team();
        // BeanUtils.copyProperties BeanUtils.copyProperties方法简单来说就是将两个字段相同的对象进行属性值的复制。
        // 如果两个对象之间存在名称不相同的属性，则 BeanUtils 不对这些属性进行处理，需要程序手动处理。
        // 如果两个对象的字段其中一个对象没有则不操作
        BeanUtils.copyProperties(teamQuery, team);
        // 这里 teamQuery 对象是前端传来的队伍参数，我们的通用分页包装类的分页参数不用赋值给对象
        // 简单的来说就算只要数据库中有的参数
        Page<Team> page = new Page<>(teamQuery.getPageNum(), teamQuery.getPageSize());
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
        Page<Team> teamPage = teamService.page(page, queryWrapper);
        return ResultUtils.success(teamPage);
    }

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 查看个人已创建队伍
     * @param teamQuery 查询条件参数
     * @param request 请求体参数
     * @return 个人已创建的队伍列表
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long userId = loginUser.getId();
        teamQuery.setUserId(userId);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        List<TeamUserVO> hasJoinSignTeamList = teamService.isUserJoinTeam(teamList, request);
        return ResultUtils.success(hasJoinSignTeamList);
    }

    /**
     * 查看个人已加入队伍
     * @param teamQuery 查询条件参数
     * @param request 请求体参数
     * @return 个人已加入的队伍列表
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long userId = loginUser.getId();
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", userId);
        List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
        Map<Long, List<UserTeam>> listMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        List<Long> idList = new ArrayList<>(listMap.keySet());
        teamQuery.setIdList(idList);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        List<TeamUserVO> hasJoinSignTeamList = teamService.isUserJoinTeam(teamList, request);
        return ResultUtils.success(hasJoinSignTeamList);
    }
}
