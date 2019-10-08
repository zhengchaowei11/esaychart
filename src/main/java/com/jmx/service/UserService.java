package com.jmx.service;

import com.jmx.netty.ChatData;
import com.jmx.netty.ChatHandler;
import com.jmx.pojo.ChatMsg;
import com.jmx.pojo.Users;
import com.jmx.vo.FriendRequestVo;
import com.jmx.vo.MyFriendsVo;

import java.util.List;

public interface UserService {
    //查询用户名是否存在
    public Boolean queryUserNameIsExist(String username);


    public Users queryUserForLogin(String username,String pwd);


    public Users  saveUser(Users user);


    public Users updateUser(Users users);

    public Integer preconditionSearchFriends(String myUserId,String friendUserName);

    public Users queryUserInfoByUserName(String username);

    void sendAddFriendRequest(String myUserId, String userName);

    public List<FriendRequestVo> queryFriendRequestList(String acceptUserId);

    public void deleteFriendRequest(String acceptUserId,String sendUserId);

    public void passFriendRequest(String acceptUserId,String sendUserId);

    List<MyFriendsVo> queryMyFriends(String userId);


    public String saveMsg(ChatData chatData);

    void updateMsgSigned(List<String> msgIdList);

    List<ChatMsg> getUnReadMsgList(String acceptId);
}
