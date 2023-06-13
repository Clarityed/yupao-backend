package com.clarity.yupaobackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clarity.yupaobackend.common.BaseResponse;
import com.clarity.yupaobackend.common.ErrorCode;
import com.clarity.yupaobackend.exception.BusinessException;
import com.clarity.yupaobackend.model.domain.User;
import com.clarity.yupaobackend.model.request.UserLoginRequest;
import com.clarity.yupaobackend.model.request.UserRegisterRequest;
import com.clarity.yupaobackend.service.UserService;
import com.clarity.yupaobackend.utils.ResultUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static com.clarity.yupaobackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户接口
 *
 * @author: clarity
 * @date: 2022年08月11日 10:48
 */
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {"http://localhost:5173/", "http://localhost:8000/"}, allowCredentials = "true")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求
     * @return 通用返回类
     */
    @PostMapping("/register")
    // 此处@RequestBody的意义是：使得前端的请求的数据会去，对于此注册请求体类
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        // 为了规范我们这里要继续参数的校验，但这里并不是业务的校验（业务越少越好）
        if (userRegisterRequest == null) {
//            return ResultUtils.error(ErrorCode.PARAM_ERROR);
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String userCode = userRegisterRequest.getUserCode();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, userCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        long result = userService.userRegister(userAccount, userPassword, checkPassword, userCode);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求
     * @param request HttpServletRequest
     * @return 通用返回类
     */
    @PostMapping("/login")
    // 此处@RequestBody的意义是：使得前端的请求的数据会去，对于此注册请求体类
    public BaseResponse<User> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 为了规范我们这里要继续参数的校验，但这里并不是业务的校验（业务越少越好）
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User user = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(user);
    }

    /**
     * 用户注销
     *
     * @param request HttpServletRequest
     * @return 通用返回类
     */
    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "请求为空");
        }
        int i = userService.userLogout(request);
        return ResultUtils.success(i);
    }

    /**
     * 获取当前在线用户
     *
     * @param request HttpServletRequest
     * @return 通用返回类
     */
    @GetMapping("/current")
    public BaseResponse<User> getCurrent(HttpServletRequest request) {
        User currentUser = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        if (currentUser == null) {
            // 发现问题：前端的全局入口文件，在程序启动时会最先启动，里面定义了获取当前用户信息的方法，那么就会执行该方法，
            // 结果是用户信息为空，那么就会抛出异常，返回给前端，但是按照响应的方式返回，前端会误认为是有用户存在，然后直接跳转到主页，这是一个bug，待修改。
            /*throw new BusinessException(ErrorCode.PARAM_ERROR);*/
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        // 之后可能要优化，可能用户会被封号。
        Long id = currentUser.getId();
        // todo 校验用户是否合法
        User user = userService.getById(id);
        User safetyUser = userService.getSafetyUser(user);
        return ResultUtils.success(safetyUser);
    }

    /**
     * 获取所有用户（仅管理员可用）
     *
     * @param username 用户昵称
     * @param request HttpServletRequest
     * @return 通用返回类
     */
    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(String username, HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "请求为空");
        }
        List<User> list = userService.searchUsers(username, request);
        return ResultUtils.success(list);
    }

    /**
     * 通过用户标签精确查询用户
     *
     * @param tagNameList 用户标签列表
     * @return 通用返回类
     */
    // @RequestParam 初步理解为关闭 SpringBoot 的错误信息返回，采用我们自己的错误信息返回
    @GetMapping("/search/tags")
    public BaseResponse<List<User>> searchUsersByTags(@RequestParam(required = false) List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<User> userList = userService.searchUsersByTags(tagNameList);
        return ResultUtils.success(userList);
    }

    /**
     * 分页查询，推荐用户（前端未实现分页）
     *
     * @param pageSize 页面大小
     * @param pageNum 当前页数
     * @param request HttpServletRequest
     * @return 通用返回类
     */
    @GetMapping("/recommend")
    public BaseResponse<Page<User>> recommendUsers(long pageSize, long pageNum, HttpServletRequest request) {
        Page<User> userPage = userService.recommendUsers(pageSize, pageNum, request);
        return ResultUtils.success(userPage);
    }

    /**
     * 用户删除（仅管理员可用）
     *
     * @param id 用户 id
     * @param request HttpServletRequest
     * @return 通用返回类
     */
    @DeleteMapping("/")
    public BaseResponse<Integer> deleteUser(@RequestBody long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户ID不合法");
        }
        int i = userService.deleteUser(id, request);
        return ResultUtils.success(i);
    }

    /**
     * 用户更新
     *
     * @param user 用户信息
     * @param request HttpServletRequest
     * @return 通用返回类
     */
    @PostMapping("/update")
    public BaseResponse<Integer> updateUser(@RequestBody User user, HttpServletRequest request) {
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        int result = userService.updateUser(user, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 用户匹配
     *
     * @param num 要展示的用户数
     * @param request 请求体
     * @return 用户列表
     */
    @GetMapping("/match")
    public BaseResponse<List<User>> matchUsers(long num, HttpServletRequest request) {
        if (num <= 0 || num > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.matchUsers(num, loginUser));
    }

}
