package com.jmx.service.impl;

import com.jmx.enums.MsgActionEnum;
import com.jmx.enums.MsgSignFlagEnum;
import com.jmx.enums.OperatorFriendRequestTypeEnum;
import com.jmx.enums.PreSearchFriendsEnum;
import com.jmx.mapper.*;
import com.jmx.netty.ChatData;
import com.jmx.netty.DataContent;
import com.jmx.netty.UserChannelRelation;
import com.jmx.pojo.ChatMsg;
import com.jmx.pojo.FriendsRequest;
import com.jmx.pojo.MyFriends;
import com.jmx.pojo.Users;
import com.jmx.service.UserService;
import com.jmx.utils.*;
import com.jmx.vo.FriendRequestVo;
import com.jmx.vo.MyFriendsVo;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.commons.lang3.StringUtils;
import org.n3r.idworker.Sid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tk.mybatis.mapper.entity.Example;
import tk.mybatis.mapper.entity.Example.Criteria;

import java.io.IOException;
import java.util.Date;
import java.util.List;


@Service
public class UserServiceImpl implements UserService{

    @Autowired
    UsersMapper usersMapper;

    @Autowired
    private Sid sid;

    @Autowired
    private QRCodeUtils qrCodeUtils;

    @Autowired
    FastDFSClient fastDFSClient;

    @Autowired
    MyFriendsMapper myFriendsMapper;

    @Autowired
    FriendsRequestMapper friendsRequestMapper;

    @Autowired
    UsersMapperCustom usersMapperCustom;

    @Autowired
    ChatMsgMapper chatMsgMapper;



    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public Boolean queryUserNameIsExist(String username) {
        Users users = new Users();
        users.setUsername(username);
        Users one = usersMapper.selectOne(users);
        return one != null ? true : false;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public Users queryUserForLogin(String username, String pwd) {
        Example userExample = new Example(Users.class);
        Criteria criteria = userExample.createCriteria();
        criteria.andEqualTo("username",username);
        criteria.andEqualTo("password",pwd);
        Users result = usersMapper.selectOneByExample(userExample);
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Users saveUser(Users user) {
        String userId = sid.nextShort();
        //为每个用户生成唯一的id
        String qrCodePath = "G://user"+userId+"qrode.png";
        qrCodeUtils.createQRCode(qrCodePath,"weixin_qrode:" + user.getUsername());
        MultipartFile qrCodeFile = FileUtils.fileToMultipart(qrCodePath);
        String qrCodeUrl = "";
        try {
            qrCodeUrl = fastDFSClient.uploadQRCode(qrCodeFile);
            System.out.println(qrCodeUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }
        user.setQrcode(qrCodeUrl);
        user.setId(userId);
        usersMapper.insert(user);
        return user;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Users updateUser(Users users) {
        //根据已有的条件进行更新，不会进行全部更新  和方法updateByPrimaryKsy  这个方法必须有主键
        usersMapper.updateByPrimaryKeySelective(users);
        return queryUserById(users.getId());
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public Integer preconditionSearchFriends(String myUserId, String friendUserName) {
        Users users = this.queryUserInfoByUserName(friendUserName);
        if(users == null){
            return PreSearchFriendsEnum.USER_NOT_FOUND.getStatus();  //得到不是同一个结果
        }


        if(users.getId().equals(myUserId)){
            return PreSearchFriendsEnum.USER_CAN_NOT_BE_YOURSELF.getStatus();
        }

        Example example = new Example(MyFriends.class);
        Criteria criteria = example.createCriteria();
        criteria.andEqualTo("myUserId",myUserId);
        criteria.andEqualTo("myFriendUserId",users.getId());
        MyFriends one = myFriendsMapper.selectOneByExample(example);
        if (one != null){
            return PreSearchFriendsEnum.USER_ALREADY_BE_FRIEND.getStatus();
        }

        return PreSearchFriendsEnum.SUCCESS.getStatus();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Users queryUserInfoByUserName(String username) {
        Example example = new Example(Users.class);//根据条件来查询
        Criteria criteria = example.createCriteria();
        criteria.andEqualTo("username",username);
        Users users = usersMapper.selectOneByExample(example);
        return users;
    }



    //方法名 根据用户名查询用户的信息

    private Users queryUserById(String userId){
        return usersMapper.selectByPrimaryKey(userId);
    }



    //发送添加朋友的请求
    @Override
    public void sendAddFriendRequest(String myUserId, String userName) {
        Users friendUser = this.queryUserInfoByUserName(userName);
        String friendUserId = friendUser.getId();
        //查询请求添加朋友的记录表中是不是有数据
        Boolean request = queryFriendRequest(myUserId, friendUserId);

        //不能进行重复的添加
        if(request){
            FriendsRequest friendsRequest = new FriendsRequest();
            friendsRequest.setId(sid.nextShort());
            friendsRequest.setSendUserId(myUserId);
            friendsRequest.setAcceptUserId(friendUserId);
            friendsRequest.setRequestDataTime(new Date());
            friendsRequestMapper.insert(friendsRequest);
        }
    }

    @Override
    public List<FriendRequestVo> queryFriendRequestList(String acceptUserId) {

        return usersMapperCustom.queryFriendRequestList(acceptUserId) ;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteFriendRequest(String acceptUserId, String sendUserId) {
        Example example = new Example(FriendsRequest.class);
        Criteria criteria = example.createCriteria();
        criteria.andEqualTo("sendUserId",sendUserId);
        criteria.andEqualTo("acceptUserId",acceptUserId);
        friendsRequestMapper.deleteByExample(example);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void passFriendRequest(String acceptUserId, String sendUserId) {
        //删除好友的请求
        this.saveFriend(acceptUserId,sendUserId);
        this.saveFriend(sendUserId,acceptUserId);

        this.deleteFriendRequest(acceptUserId,sendUserId);

        Channel sendChannel = UserChannelRelation.get(sendUserId);
        if (sendChannel != null) {
            // 使用websocket主动推送消息到请求发起者，更新他的通讯录列表为最新
            DataContent dataContent = new DataContent();
            dataContent.setAction(MsgActionEnum.PULL_FRIEND.type);

            sendChannel.writeAndFlush(
                    new TextWebSocketFrame(
                            JsonUtils.objectToJson(dataContent)));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<MyFriendsVo> queryMyFriends(String userId) {
        return usersMapperCustom.queryMyFriends(userId) ;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public String saveMsg(ChatData chatData) {
        //保存数据
        String msgId = sid.nextShort();
        ChatMsg chatMsg = new ChatMsg();
        chatMsg.setAcceptUserId(chatData.getReceiverId());
        chatMsg.setSendUserId(chatData.getSenderId());
        chatMsg.setMsg(chatData.getMsg());
        chatMsg.setCreateTime(new Date());
        chatMsg.setSignFlag(false);
        chatMsg.setId(msgId);
        chatMsgMapper.insert(chatMsg);
        return msgId;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateMsgSigned(List<String> msgIdList) {
        usersMapperCustom.batchUpdateMsgSigned(msgIdList);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<ChatMsg> getUnReadMsgList(String acceptId) {
        Example chatMsgExample = new Example(ChatMsg.class);
        //配置查询的条件
        Criteria criteriaMsg = chatMsgExample.createCriteria();
        criteriaMsg.andEqualTo("acceptUserId",acceptId);
        criteriaMsg.andEqualTo("signFlag",0);
        List<ChatMsg> chatMsgList = chatMsgMapper.selectByExample(chatMsgExample);
        return chatMsgList;
    }


    private Boolean queryFriendRequest(String myUserId, String friendUserId ){
        Example example = new Example(FriendsRequest.class);
        Criteria criteria = example.createCriteria();
        criteria.andEqualTo("sendUserId",myUserId);
        criteria.andEqualTo("acceptUserId",friendUserId);
        FriendsRequest friendsRequest = friendsRequestMapper.selectOneByExample(example);
        return friendsRequest == null ;
    }


    private void saveFriend(String acceptUserId, String sendUserId){
        //创建朋友
        MyFriends myFriends = new MyFriends();
        myFriends.setId(sid.nextShort());
        myFriends.setMyUserId(acceptUserId);
        myFriends.setMyFriendUserId(sendUserId);

        //
        myFriendsMapper.insert(myFriends);
    }



}
