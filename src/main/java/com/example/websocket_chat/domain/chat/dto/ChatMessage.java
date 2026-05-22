package com.example.websocket_chat.domain.chat.dto;

public class ChatMessage {
    private String user;
    private String msg;
    private long time;

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }
}
