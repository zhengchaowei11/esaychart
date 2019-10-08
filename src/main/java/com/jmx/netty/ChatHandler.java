package com.jmx.netty;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.jmx.enums.MsgActionEnum;
import com.jmx.service.UserService;
import com.jmx.utils.JsonUtils;
import com.jmx.utils.SpringUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @Description: 处理消息的handler
 * TextWebSocketFrame： 在netty中，是用于为websocket专门处理文本的对象，frame是消息的载体
 */
public class ChatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    // 用于记录和管理所有客户端的channle
    private static ChannelGroup users =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg)
            throws Exception {

        Channel currentChannel = ctx.channel();
        // 获取客户端传输过来的消息
        String content = msg.text();
        System.out.println("客户端发送过来的消息是："+ content);
        //将客户端的数据传送到的消息 进行判断 ，不同的类型使用不同的枚举类型
        DataContent dataContent = JsonUtils.jsonToPojo(content, DataContent.class);
        Integer action = dataContent.getAction();
        if (action == MsgActionEnum.CONNECT.type){
            //2.1当websock  第一open的时候，初始化channel ,把用channel 和 userid关联起来
            String senderId = dataContent.getChatData().getSenderId();
            //用户的id和当前的currentChannel进行绑定
            UserChannelRelation.put(senderId,currentChannel);


            //测试
            for (Channel c : users){
                System.out.println(c.id().asLongText());
            }
            UserChannelRelation.output();
        }else if (action == MsgActionEnum.CHAT.type){
            //2.2 把聊天的消息保存到数据库中，同时标记消息的状态为未签收
            ChatData chatData = dataContent.getChatData();
            String msgText = chatData.getMsg();
            String receiverId = chatData.getReceiverId();
            String senderId = chatData.getSenderId();

            //保存消息到数据库中，并且标记为 未签收
            UserService userService = (UserService) SpringUtil.getBean("userServiceImpl");
            String msgId = userService.saveMsg(chatData);
            chatData.setMsgId(msgId);

            DataContent dataContentMsg = new DataContent();
            dataContentMsg.setChatData(chatData);

            Channel receiveChannel = UserChannelRelation.get(receiverId);

            if (receiveChannel == null){
                //为空代表用户离线，推送消息（JPush,各退，小米）
            }else {
                Channel findChannel = users.find(receiveChannel.id());
                if (findChannel != null){
                    receiveChannel.writeAndFlush(new TextWebSocketFrame(JsonUtils.objectToJson(dataContentMsg)));

                }else {
                    //用户离线
                }
            }

        }else if (action == MsgActionEnum.SIGNED.type){
            //2.3 签收消息类型，针对具体的消息进行签收，修改数据库中的对应消息的签收状态（已经签收）
            UserService userService = (UserService) SpringUtil.getBean("userServiceImpl");
            String msgIds = dataContent.getExtend();
            String[] split = msgIds.split(",");
            List<String> msgIdList= new ArrayList<>();
            for (String msgId : split){
                if(StringUtils.isNotEmpty(msgId)){
                    msgIdList.add(msgId);
                }
            }

            if (msgIdList != null && !msgIdList.isEmpty() && msgIdList.size() > 0){
                userService.updateMsgSigned(msgIdList);
            }



        }else if (action == MsgActionEnum.KEEPALIVE.type){

        }


    }

    /**
     * 当客户端连接服务端之后（打开连接）
     * 获取客户端的channle，并且放到ChannelGroup中去进行管理
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        users.add(ctx.channel());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // 当触发handlerRemoved，ChannelGroup会自动移除对应客户端的channel
//		clients.remove(ctx.channel());
        System.out.println("客户端断开，channle对应的长id为："
                + ctx.channel().id().asLongText());
        System.out.println("客户端断开，channle对应的短id为："
                + ctx.channel().id().asShortText());
    }



}
