package com.jmx.controller;

import com.jmx.bo.UsersBo;
import com.jmx.enums.OperatorFriendRequestTypeEnum;
import com.jmx.enums.PreSearchFriendsEnum;
import com.jmx.pojo.ChatMsg;
import com.jmx.pojo.Users;
import com.jmx.service.UserService;
import com.jmx.service.impl.UserServiceImpl;
import com.jmx.utils.FastDFSClient;
import com.jmx.utils.FileUtils;
import com.jmx.utils.MD5Utils;
import com.jmx.utils.ResponseResult;
import com.jmx.vo.MyFriendsVo;
import com.jmx.vo.UsersVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.Id;
import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/u")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    FastDFSClient fastDFSClient;
    @PostMapping("/registOrLogin")
    public ResponseResult registOrLogin(@RequestBody Users user) throws Exception{
        if (StringUtils.isEmpty(user.getUsername())
                || StringUtils.isEmpty(user.getPassword())){
            return ResponseResult.errorMsg("用户名或者密码不能为空");
        }

        Boolean userNameIsExist = userService.queryUserNameIsExist(user.getUsername());
        Users userResult = null;
        if (userNameIsExist){
            //用户名存在的话，就进行登陆的操作
            //登陆的操作
            userResult = userService.queryUserForLogin(user.getUsername(), MD5Utils.getMD5Str(user.getPassword()));
            if (userResult == null){
                return ResponseResult.errorMsg("用户名或者密码为空");
            }
        }else {
            user.setNickname(user.getUsername());
            user.setFaceImage("");
            user.setFaceImageBig("");
            user.setPassword(MD5Utils.getMD5Str(user.getPassword()));
            userResult = userService.saveUser(user);
        }

        UsersVo usersVo = new UsersVo();
        BeanUtils.copyProperties(userResult,usersVo);
        return ResponseResult.ok(userResult);
    }


    @PostMapping("/uploadFaceBase64")
    public ResponseResult uploadFaceBase64(@RequestBody UsersBo usersBo) throws Exception {
        //1.将前端传过来的base64字符串转化成对象
        String faceData = usersBo.getFaceData();
        String userFacePath = "G:\\"+usersBo.getUserId() + "userface64.png";
        FileUtils.base64ToFile(userFacePath, faceData);


        //2上传文件的到fastdfs

        //将文件转化成多媒体文件 multipartFile
        MultipartFile multipartFile = FileUtils.fileToMultipart(userFacePath);

        String url = fastDFSClient.uploadBase64(multipartFile);
        System.out.println(url);


        //返回大图，拼接小图
        String thump = "_80x80.";
        String[] arr = url.split("\\.");
        String thumpUrl = arr[0]+thump+arr[1];


        //更新用户的头像信息
        Users users = new Users();
        users.setId(usersBo.getUserId());
        users.setFaceImageBig(url);
        users.setFaceImage(thumpUrl);

        //上传到数据库信息
        Users userInfo = userService.updateUser(users);

        UsersVo usersVo = new UsersVo();

        BeanUtils.copyProperties(userInfo,usersVo);

        //返回数据到数据库中
        return new ResponseResult(usersVo);

    }


    @PostMapping("/setNickname")
    public ResponseResult setNickname(@RequestBody UsersBo usersBo){
        Users users = new Users();
        users.setId(usersBo.getUserId());
        users.setNickname(usersBo.getNickname());
        Users usersInfo = userService.updateUser(users);
        return ResponseResult.ok(usersInfo);
    }


    @PostMapping("/searchFriends")
    public ResponseResult searchFriends(String myUserId,String userName){
        if (StringUtils.isEmpty(myUserId) || StringUtils.isEmpty(userName)){
            return ResponseResult.errorMsg("账号不存在的");
        }

        //前置条件
        //1.如果用户不存在的
        //2.如果用户搜索到的是自己的话
        //3.如果用户搜索到的用户，已经添加了好友
        Integer status = userService.preconditionSearchFriends(myUserId, userName);
        if(status ==  PreSearchFriendsEnum.SUCCESS.getStatus()){
            Users users = userService.queryUserInfoByUserName(userName);
            //因为UsersVo里面没有password而 Users里面是有password
            UsersVo usersVo = new UsersVo();
            BeanUtils.copyProperties(users,usersVo);
            return ResponseResult.ok(usersVo);
        }else {
            String errorMsg = PreSearchFriendsEnum.getMsgByStatus(status);
            return ResponseResult.errorMsg(errorMsg);
        }
    }


    @PostMapping("/sendAddFriendRequest")
    public ResponseResult sendAddFriendRequest(String myUserId,String userName){
        if (StringUtils.isEmpty(myUserId) || StringUtils.isEmpty(userName)){
            return ResponseResult.errorMsg(" ");
        }

        //前置条件
        //1.如果用户不存在的
        //2.如果用户搜索到的是自己的话
        //3.如果用户搜索到的用户，已经添加了好友
        Integer status = userService.preconditionSearchFriends(myUserId, userName);
        if(status ==  PreSearchFriendsEnum.SUCCESS.getStatus()){
            userService.sendAddFriendRequest(myUserId,userName);
            return ResponseResult.ok();
        }else {
            String errorMsg = PreSearchFriendsEnum.getMsgByStatus(status);
            return ResponseResult.errorMsg(errorMsg);
        }
    }

    @PostMapping("/queryFriendRequests")
    public ResponseResult queryFriendRequests(String acceptUserId){
        if (StringUtils.isEmpty(acceptUserId)){
            return ResponseResult.errorMsg("");
        }
        return ResponseResult.ok(userService.queryFriendRequestList(acceptUserId));
    }


    @PostMapping("/operatorFriendRequest")
    public ResponseResult queryFriendRequests(String acceptUserId,String sendUserId,Integer operaType){
        if(StringUtils.isEmpty(acceptUserId)
                || StringUtils.isEmpty(sendUserId)
                || operaType == null){
            return ResponseResult.errorMsg("空值");
        }
        if(OperatorFriendRequestTypeEnum.getMsgByType(operaType) == null){
            return ResponseResult.errorMsg("发生类型的错误");
        }

        if ( OperatorFriendRequestTypeEnum.IGNORE.type == operaType){
            //忽略，直接删除请求的信息
            userService.deleteFriendRequest(acceptUserId, sendUserId);
            return ResponseResult.ok() ;
        }else if (OperatorFriendRequestTypeEnum.PASS.type == operaType){
            //通过，删除请求的信息，进行互相添加好友的记录
            userService.passFriendRequest(acceptUserId, sendUserId);

        }
        return ResponseResult.ok();

    }


    @PostMapping("/queryMyFriends")
    public ResponseResult queryMyFriends(String userId){
        //1. 做非空判断
        if(StringUtils.isBlank(userId)){
            return ResponseResult.errorMsg("");
        }
        //2. 数据库查询我的好友
        List<MyFriendsVo> myFriendsVos = userService.queryMyFriends(userId);
        System.out.println(myFriendsVos);
        return ResponseResult.ok(myFriendsVos);
    }

    @PostMapping("/getUnReadMsgList")
    //获取未读消息列表
    public ResponseResult getUnReadMsgList(String acceptUserId){
        //1. 做非空判断
        if(StringUtils.isBlank(acceptUserId)){
            return ResponseResult.errorMsg("");
        }

        List<ChatMsg> chatMsgList = userService.getUnReadMsgList(acceptUserId);

        System.out.println(chatMsgList);
        return ResponseResult.ok(chatMsgList);
    }












}
