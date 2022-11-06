package com.clarity.yupaobackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.clarity.yupaobackend.model.domain.UserTeam;
import com.clarity.yupaobackend.mapper.UserTeamMapper;
import com.clarity.yupaobackend.service.UserTeamService;
import org.springframework.stereotype.Service;

/**
* @author Clarity
* @description 针对表【user_team(用户队伍关系)】的数据库操作Service实现
* @createDate 2022-09-30 10:05:23
*/
@Service
public class UserTeamServiceImpl extends ServiceImpl<UserTeamMapper, UserTeam>
    implements UserTeamService{

}




