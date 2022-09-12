package com.project.uandmeet.service;

import com.project.uandmeet.dto.*;
import com.project.uandmeet.exception.CustomException;
import com.project.uandmeet.exception.ErrorCode;
import com.project.uandmeet.model.Board;
import com.project.uandmeet.model.Member;
import com.project.uandmeet.model.Review;
import com.project.uandmeet.repository.BoardRepository;
import com.project.uandmeet.repository.EntryRepository;
import com.project.uandmeet.repository.ReviewRepository;
import com.project.uandmeet.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final BoardRepository boardRepository;
    private final EntryRepository entryRepository;

    public ReviewResponseDto review(UserDetailsImpl userDetails, BoardIdRequestDto requestDto) {
        String nickname = userDetails.getMember().getNickname();
        Board board = boardRepository.findByBoardTypeAndId("matching", requestDto.getBoardId()).orElseThrow(
                () -> new RuntimeException("찾을 수 없는 게시글입니다.")
        );
        Member otherMember = board.getMember();
        ReviewResponseDto reviewResponseDto = new ReviewResponseDto(nickname, otherMember);
        return reviewResponseDto;
    }


    public ReviewDto createReview(UserDetailsImpl userDetails, ReviewRequestDto requestDto) throws ParseException {
        Member from = userDetails.getMember();
        Board board = boardRepository.findByBoardTypeAndId("matching", requestDto.getBoardId()).orElseThrow(
                () -> new RuntimeException("찾을 수 없는 게시글입니다.")
        );
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); // 형식 통일
        Date date = sdf.parse(board.getEndDateAt());//String-->Date
        Long dateLong = date.getTime();//Date-->Long
        long now = Timestamp.valueOf(LocalDateTime.now()).getTime(); // 현재

        // 해당 매칭에 참여하지 않았거나 매챙 만료일이 지나지 않을 시
        if (!(entryRepository.existsByMemberAndAndBoard(from, board)) || (dateLong > now)) {
            throw new CustomException(ErrorCode.INVALID_AUTHORITY);
        }
        // 이미 참여한 리뷰일 경우
        if (reviewRepository.existsByToAndBoard(board.getMember(), board)) {
            throw new CustomException(ErrorCode.DUPLICATE_REVIEW);
        }

        Review review = new Review(board, from.getId(), board.getMember(), requestDto.getNum(), requestDto.getScore(), requestDto.getReview());
        reviewRepository.save(review);
        return new ReviewDto(board.getId(),
                from.getId(),
                board.getMember().getId(),
                board.getMember().getNickname(),
                requestDto.getNum(),
                requestDto.getScore(),
                requestDto.getReview());
    }
}
