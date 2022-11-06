package com.clarity.yupaobackend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.clarity.yupaobackend.mapper.UserMapper;
import com.clarity.yupaobackend.model.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;


@SpringBootTest
class YuPaoBackendApplicationTests {

    @Resource
    private UserMapper userMapper;

    @Test
    void contextLoads() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", "clarity");
        List<User> users = userMapper.selectList(queryWrapper);
        for (User user : users) {
            System.out.println(user);
        }
    }

}
