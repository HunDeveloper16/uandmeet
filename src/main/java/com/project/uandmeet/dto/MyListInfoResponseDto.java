package com.project.uandmeet.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MyListInfoResponseDto {
    private Long boardId;
    //정보공유 : information,  매칭:matching 둘중 하나.
    private String boardType;
    private String category;
    private String title;
    private String content;
    //좋아요 수
    private Long likeCount;
    //조회 수
    private Long viewCount;
    //댓글 수
    private Long commentCount;

    private String boardimage;
    private String createdAt;
    private String modifiedAt;

    private MyListMemberResponseDto writer;

    public MyListInfoResponseDto(Long boardId, String boardType, String category, String title, String content, Long likeCount, Long viewCount, Long commentCount, String boardimage, String createdAt, String modifiedAt, MyListMemberResponseDto writer) {
        this.boardId = boardId;
        this.boardType = boardType;
        this.category = category;
        this.title = title;
        this.content = content;
        this.likeCount = likeCount;
        this.viewCount = viewCount;
        this.commentCount = commentCount;
        this.boardimage = boardimage;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.writer = writer;
    }
}