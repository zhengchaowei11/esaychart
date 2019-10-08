package com.jmx.mapper;

import com.jmx.pojo.Users;
import com.jmx.utils.MyMapper;
import com.jmx.vo.FriendRequestVo;
import com.jmx.vo.MyFriendsVo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface UsersMapperCustom extends MyMapper<Users> {
    public List<FriendRequestVo> queryFriendRequestList(String acceptUserId);

    public List<MyFriendsVo> queryMyFriends(String userId);

    //批量更新数据库中的标识
    public void batchUpdateMsgSigned(List<String> msgIdList);
}