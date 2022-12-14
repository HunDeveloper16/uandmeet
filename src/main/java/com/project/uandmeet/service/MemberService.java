package com.project.uandmeet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.uandmeet.dto.*;
import com.project.uandmeet.dto.boardDtoGroup.LikeDto;
import com.project.uandmeet.exception.CustomException;
import com.project.uandmeet.exception.ErrorCode;
import com.project.uandmeet.model.*;
import com.project.uandmeet.redis.RedisUtil;
import com.project.uandmeet.repository.*;
import com.project.uandmeet.security.UserDetailsImpl;
import com.project.uandmeet.security.jwt.JwtProperties;
import com.project.uandmeet.security.jwt.JwtTokenProvider;
import com.project.uandmeet.service.S3.S3Uploader;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Transactional
@RequiredArgsConstructor
@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final EntryRepository entryRepository;
    private final ReviewRepository reviewRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final LikedRepository likedRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RedisUtil redisUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final S3Uploader s3Uploader;
    private final String POST_IMAGE_DIR = "static";
    private int emailCnt = 0; // email ?????? ??????

    // ?????? ??????
    public String makeRandomNumber() {
        String checkNum = UUID.randomUUID().toString().substring(0, 6);
        System.out.println("?????? ???????????? : " + checkNum);
        return checkNum;
    }

    // ?????? ?????? 1. emali check
    public String checkemail(String username) {
        checkEmail(username);

        // email ?????? ??????
        checkDuplicateEmail(username);
        return "email check";
    }

    private void checkEmail(String username) {
        String[] emailadress = username.split("@");
        String id = emailadress[0];
        String host = emailadress[1];
        String pattern = "^[a-zA-Z0-9_!#$%&'\\*+/=?{|}~^.-]+@[a-zA-Z0-9.-]+.[a-zA-Z0-9.-]*$";
        String idpattern = "^[a-zA-Z0-9_!#$%&'\\*+/=?{|}~^.-]*$";
        String hostpattern = "^[a-zA-Z0-9.-]*$";
        // email ??????
        // ID ?????? ????????????, ??????, _!#$%&'\*+/=?{|}~^.- ????????????
        // Host ????????? @, ?????? ????????????, ??????, .-????????????

        // ???????????? username ??????
        if (username.length() < 10) {
            throw new IllegalArgumentException("???????????? 10??? ?????? ???????????????");
        } else if (!Pattern.matches(idpattern, id)) {
            throw new IllegalArgumentException("id??? ????????? ??????????????? ??????, ????????????( _!#$%&'\\*+/=?{|}~^.-)?????? ???????????????");
        } else if (!Pattern.matches(hostpattern, host)) {
            throw new IllegalArgumentException("host??? ????????? ??????????????? ??????, ????????????(.-)?????? ???????????????");
        } else if (!Pattern.matches(pattern, username)) {
            throw new IllegalArgumentException("????????? ????????? ?????? ???????????????");
        } else if (username.contains("script")) {
            throw new IllegalArgumentException("xss?????? ???????????????.");
        }
    }


    // ????????????, ???????????? ????????? ??????
    public String checkPassword(String password, String passwordCheck) {
        if (password.length() < 3) {
            throw new IllegalArgumentException("??????????????? 3??? ?????? ???????????????");
        } else if (password.length() > 21) {
            throw new IllegalArgumentException("??????????????? 20??? ????????? ???????????????");
        } else if (password.contains("script")) {
            throw new IllegalArgumentException("xss?????? ???????????????.");
        } else if (passwordCheck.contains("script")) {
            throw new IllegalArgumentException("xss?????? ???????????????.");
        }
        if (!(passwordCheck.equals(password))) {
            throw new IllegalArgumentException("??????????????? ???????????? ????????????.");
        }
        return "password check ??????";
    }

    public String signup(MemberRequestDto requestDto) {
        String username = requestDto.getUsername();
        String[] emailadress = username.split("@");
        String id = emailadress[0];
        String uuid = UUID.randomUUID().toString().substring(0, 5);
        String uniqueId = id + uuid;
        // ????????? ?????? ??????
        checkEmail(username);
        // ????????? ?????? ??????
        checkDuplicateEmail(username);
        checkPassword(requestDto.getPassword(), requestDto.getPasswordCheck());
        Member member = requestDto.register();
        member.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        // login ??????
        member.setLoginto("normal");
        // ?????? ?????????
        member.setNickname(uniqueId);

        // ????????? ????????? ??????
//        if (requestDto.getUserProfileImage() != null) {
//            String profileUrl = s3Uploader.upload(requestDto.getUserProfileImage(), "profile");
//            users.setUserProfileImage(profileUrl);
//        }

        memberRepository.save(member);
        return "???????????? ??????";
    }


    public void checkDuplicateEmail(String username) {
        Optional<Member> member = memberRepository.findByUsername(username);
        if (member.isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
        }
    }


    // ?????? ??????
    @Transactional
    public ResponseEntity<String> withdraw(UserDetailsImpl userDetails, String password) {
        ResponseEntity<String> responseEntity = null;

        if (passwordEncoder.matches(password,userDetails.getPassword())) {
            String username = userDetails.getUsername();

            List<Entry> entries = entryRepository.findByMember(userDetails.getMember());
            List<Liked> likeds = likedRepository.findByMember(userDetails.getMember());
            List<Comment> commentList = commentRepository.findByMember(userDetails.getMember());

            //?????? ?????? ?????? ?????? ?????????.
            for (Entry entry : entries) {
                Board board = boardRepository.findById(entry.getBoard().getId())
                        .orElseGet(() -> null);

                if (board != null) {
                    board.setCurrentEntry(board.getCurrentEntry() - 1);
                    boardRepository.save(board);
                }
                entryRepository.delete(entry);
            }

            //????????? ?????? ?????? ?????? ?????????.
            for (Liked liked : likeds) {
                Board board = boardRepository.findById(liked.getBoard().getId())
                        .orElseGet(() -> null);

                if (board != null) {
                    board.setLikeCount(board.getLikeCount() - 1);
                    boardRepository.save(board);
                }

                likedRepository.delete(liked);
            }

            //?????? ?????????
            for (Comment comment : commentList) {
                Board board = boardRepository.findById(comment.getBoard().getId())
                        .orElseGet(() -> null);

                if (board != null) {
                    board.setCommentCount(board.getCommentCount() - 1);
                    boardRepository.save(board);
                }

                commentRepository.delete(comment);
            }

            try {
                memberRepository.deleteByUsername(username);
                responseEntity = ResponseEntity.ok("?????? ?????? ??????.");
            }catch (Exception e)
            {
                System.out.println(e);
                responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(String.valueOf(e));
            }
        }
        else
            responseEntity = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("????????? ?????????????????????.");

        return responseEntity;
    }

//    public void accessAndRefreshTokenProcess(String username) {
//        String refreshToken = jwtTokenProvider.createRefreshToken();
//        redisUtil.setValues(refreshToken, username);
//        redisUtil.setExpire(refreshToken, 7 * 24 * 60 * 60 * 1000L, TimeUnit.MILLISECONDS);
//        jwtTokenProvider.createToken(username);
//    }

    public String refresh(HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException {

        //AccessToken
        String expiredAccessTokenHeader = request.getHeader(JwtProperties.HEADER_ACCESS);
//        String expiredAccessToken = jwtTokenProvider.setTokenName(expiredAccessTokenHeader); // barrer ??????
        if (expiredAccessTokenHeader == null || !expiredAccessTokenHeader.startsWith(JwtProperties.TOKEN_PREFIX)) {
            throw new CustomException(ErrorCode.EMPTY_CONTENT);
        }
        String expiredAccessTokenName = jwtTokenProvider.getExpiredAccessTokenPk(expiredAccessTokenHeader);
        // refreshToken
        String authorizationHeader = redisUtil.getData(expiredAccessTokenName + JwtProperties.HEADER_REFRESH);

        // ?????? ???????????? ??????
        if (!redisUtil.getData(expiredAccessTokenName + JwtProperties.HEADER_ACCESS).equals(expiredAccessTokenHeader)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // Refresh Token ????????? ??????
        jwtTokenProvider.validateToken(authorizationHeader);
        String username = jwtTokenProvider.getUserPk(authorizationHeader);
        Member member = memberRepository.findByUsername(username).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );
        Long userId = member.getId();
        // Access Token ?????????
        String accessToken = jwtTokenProvider.createToken(username, userId);

//        Map<String, String> accessTokenResponseMap = new HashMap<>();

        // ??????????????? Refresh Token ??????????????? ?????? ?????? ???????????? ?????? (???????????? ??? ???????????? ??????????????? ??????)
        // Refresh Token ???????????? ????????? ?????? ?????? ????????? ??? refresh token ??? ?????????
        Date now = new Date();
        Date refreshExpireTime = jwtTokenProvider.ExpireTime(authorizationHeader);
        if (refreshExpireTime.before(new Date(now.getTime() + 1000 * 60 * 60 * 24L))) { // refresh token ??????????????? ?????????????????? ????????? ?????????
            String newRefreshToken = jwtTokenProvider.createRefreshToken(username);
//            accessTokenResponseMap.put(JwtProperties.HEADER_REFRESH, JwtProperties.TOKEN_PREFIX + newRefreshToken);
            redisUtil.setDataExpire(jwtTokenProvider.getUserPk(accessToken) + JwtProperties.HEADER_ACCESS, JwtProperties.TOKEN_PREFIX + accessToken, JwtProperties.ACCESS_EXPIRATION_TIME);
            redisUtil.setDataExpire(jwtTokenProvider.getUserPk(accessToken) + JwtProperties.HEADER_REFRESH, JwtProperties.TOKEN_PREFIX + newRefreshToken, JwtProperties.REFRESH_EXPIRATION_TIME);
        }

//        accessTokenResponseMap.put(JwtProperties.HEADER_ACCESS, JwtProperties.TOKEN_PREFIX + accessToken);
//        Map<String, String> tokens = accessTokenResponseMap;
        response.setHeader(JwtProperties.HEADER_ACCESS, JwtProperties.TOKEN_PREFIX + accessToken);
//        if (tokens.get(JwtProperties.HEADER_REFRESH) != null) {
//            response.setHeader(JwtProperties.HEADER_REFRESH, tokens.get(JwtProperties.HEADER_REFRESH));
//        }
        return "????????? ??????";
    }

    public String findpassword(String username) {
        redisUtil.setDataExpire("passAuth" + username, makeRandomNumber(),60 * 3L);
        redisUtil.setDataExpire("passCnt" + username, String.valueOf(emailCnt),60 * 60L);
        if (Integer.parseInt(redisUtil.getData("Cnt" + username)) < 4) {
            redisUtil.setDataExpire("Cnt" + username, String.valueOf(emailCnt + 1),60 * 60L);
            int restCnt = 3 - Integer.parseInt(redisUtil.getData("Cnt" + username));

            Member member = memberRepository.findByUsername(username).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );

            //???????????? ?????????
            String setFrom = "wjdgns5488@naver.com"; // email-config??? ????????? ????????? ????????? ????????? ??????
            String toMail = username;
            String title = "???????????? ?????? ????????? ?????????."; // ????????? ??????
            String content =
                    " <div" +
                            "	style=\"font-family: 'Apple SD Gothic Neo', 'sans-serif' !important; width: 400px; height: 600px; border-top: 4px solid #00CFFF; margin: 100px auto; padding: 30px 0; box-sizing: border-box;\">" +
                            "	<h1 style=\"margin: 0; padding: 0 5px; font-size: 28px; font-weight: 400;\">" +
                            "		<span style=\"font-size: 15px; margin: 0 0 10px 3px;\">????????????</span><br />" +
                            "		<span style=\"color: #00CFFF\">????????????</span> ???????????????." +
                            "	</h1>\n" +
                            "	<p style=\"font-size: 16px; line-height: 26px; margin-top: 50px; padding: 0 5px;\">" +
                            "		???????????????.<br />" +
                            toMail +
                            "	<p style=\"font-size: 16px; line-height: 26px; margin-top: 50px; padding: 0 5px;\">" +
                            "		???<br />" +
                            "		??????????????? ???????????? ???????????????.<br />" +
                            "		<b style=\"color: #00CFFF\">'?????? ??????'</b> ??? ???????????? ???????????? ????????? ????????? ?????????.<br />" +
                            "		???????????????." +
                            "	</p>" +
                            "          <div style=\"text-align: center;\"><h1><b style=\"color: #00CFFF\" >" + redisUtil.getData("passAuth" + username) + "<br /><h1></div>" +
                            "	<div style=\"border-top: 1px solid #DDD; padding: 5px;\"></div>" +
                            "<br>" +
                            "?????? ?????? ?????? : " + restCnt +
                            " </div>";

            return "?????? ?????? :" + redisUtil.getData("passAuth" + username) + "?????? ?????? :" + restCnt;
        }
        return "?????? ????????? ?????????????????????. 1?????? ?????? ?????? ????????? ?????????.";
    }

    // ?????? ??????
    public String findCheck(String authNum, String username) {
        return String.valueOf(authNum.equals(redisUtil.getData("passAuth" + username)));
    }

    // ???????????? ??????
    public String passChange(UserDetailsImpl userDetails, PasswordChangeDto passwordChangeDto) {
        Long userId = userDetails.getMember().getId();
        Member member = memberRepository.findById(userId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );
        if (!passwordChangeDto.getNewPassword().equals(passwordChangeDto.getNewPasswordCheck())) {
            throw new CustomException(ErrorCode.PASSWORD_PASSWORDCHECK);
        } else {
            member.setPassword(passwordEncoder.encode(passwordChangeDto.getNewPassword()));
        }
        return "???????????? ?????? ??????";
    }

    // ?????? ?????? ??????
    public MypageDto action(UserDetailsImpl userDetails) {
        Long userId = userDetails.getMember().getId();
        Member member = memberRepository.findById(userId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );
        List<Entry> entry = entryRepository.findByMember(member); // ????????? ?????? ?????????
        String nickname = member.getNickname();
        List<String> concern = member.getConcern();
        Long cnt = entryRepository.countByMember(member); // ????????? ??????
        Map<String, Long> joinCnt = new HashMap<>();
        if (cnt == 0) {
            return new MypageDto(nickname, concern);
        } else {
            for (int i = 0; i < cnt; i++) {
                String category = entry.get(i).getCategory().getCategory();
                Long categoryCnt = entryRepository.countByMemberAndCategory(member, entry.get(i).getCategory());
                joinCnt.put(category, categoryCnt);
            }
            return new MypageDto(nickname, concern, joinCnt);
        }
    }


        // ???????????? -> ????????? ??????
        public MypageDto concernedit (UserDetailsImpl userDetails, String[]concerns){
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            List<Entry> entry = entryRepository.findByMember(member); // ????????? ?????? ?????????
            Long cnt = entryRepository.countByMember(member); // ????????? ?????? ???
            String nickname = member.getNickname(); // ?????????
            List<String> concern = new ArrayList<>(); // ?????????
            int idx = 0;
            for (String e : concerns) {
                if (idx > 2) {
                    break;
                }
                concern.add(e);
                idx++;
            }
            member.setConcern(concern);
            Map<String, Long> joinCnt = new HashMap<>();
            if (cnt == 0) {
                return new MypageDto(nickname, concern);
            }
            for (int i = 0; i < cnt; i++) {
                String category = entry.get(i).getCategory().getCategory();
                Long categoryCnt = entryRepository.countByMemberAndCategory(member, entry.get(i).getCategory());
                joinCnt.put(category, categoryCnt);
            }
            return new MypageDto(nickname, concern, joinCnt);
        }


        // ?????? ????????? -> ????????? ??????
        public MypageDto nicknameedit (UserDetailsImpl userDetails, String nickname){
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            List<Entry> entry = entryRepository.findByMember(member); // ????????? ?????? ?????????
            Long cnt = entryRepository.countByMember(member); // ????????? ??????
            List<String> concern = member.getConcern();
            Map<String, Long> joinCnt = new HashMap<>();
            for (int i = 0; i < cnt; i++) {
                if (entry.get(0).getBoard().getCategory().getCategory() == null) {
                    MypageDto mypageDto = new MypageDto(nickname, concern);
                    return mypageDto;
                } else {
                    String category = entry.get(i).getCategory().getCategory();
                    Long categoryCnt = entryRepository.countByMemberAndCategory(member, entry.get(i).getCategory());
                    joinCnt.put(category, categoryCnt);
                }
            }
            Member usingnickname = memberRepository.findByNickname(nickname).orElse(null);
            if (usingnickname == null) {
                member.setNickname(nickname);
            } else {
                throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
            }
            MypageDto mypageDto = new MypageDto(nickname, concern, joinCnt);
            return mypageDto;
        }

        // memberInfo ??????
        public MyPageInfoDto myinfo (UserDetailsImpl userDetails){
            String username = userDetails.getUsername();
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            String gender = member.getGender();
            Map<String, Long> birth = member.getBirth(); // year, month, day
            MyPageInfoDto myPageInfoDto = new MyPageInfoDto(username, gender, birth);
            return myPageInfoDto;
        }

        // info -> gender ??????
        public MyPageInfoDto genderedit (UserDetailsImpl userDetails, InfogenderDto requestDto){
            String username = userDetails.getUsername();
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            String gender = requestDto.getGender();
            member.setGender(gender);
            Map<String, Long> birth = member.getBirth();
            MyPageInfoDto myPageInfoDto = new MyPageInfoDto(username, gender, birth);
            return myPageInfoDto;
        }

        // info -> birth ??????
        public MyPageInfoDto birthedit (UserDetailsImpl userDetails, InfoeditRequestDto requestDto){
            String username = userDetails.getUsername();
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            String gender = member.getGender();
            Map<String, Long> birth = new HashMap<>();
            birth.put("birthYear", requestDto.getBirthYear());
            birth.put("birthMonth", requestDto.getBirthMonth());
            birth.put("birthDay", requestDto.getBirthDay());
            member.setBirth(birth);
            MyPageInfoDto myPageInfoDto = new MyPageInfoDto(username, gender, birth);
            return myPageInfoDto;
        }

        // profile ??????
        public ProfileDto profile (UserDetailsImpl userDetails){
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            List<Review> review = reviewRepository.findByTo(userId);

            String nickname = member.getNickname();
            Double sum = 0D;
            Double star;
            for (Review value : review) {
                sum += value.getEvaluation_items();
            }
            if (sum == 0) {
                star = 0D;
            } else {
                star = sum / review.size(); // ?????? ??????
            }
            String profile = member.getProfile();
            ProfileDto profileDto = new ProfileDto(nickname, star, profile);
            return profileDto;
        }

        // profile ??????
        @Transactional
        public ProfileDto profileedit (UserDetailsImpl userDetails, ProfileEditRequestDto requestDto) throws IOException
        {
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            List<Review> review = reviewRepository.findByTo(userId);
            String nickname = member.getNickname();
            Double sum = 0D;
            Double star;
            for (Review value : review) {
                sum += value.getEvaluation_items();
            }
            if (sum == 0) {
                star = 0D;
            } else {
                star = sum / review.size(); // ?????? ??????
            }
            if (requestDto.getData() != null) {
                ImageDto uploadImage = s3Uploader.upload(requestDto.getData(), POST_IMAGE_DIR);
                member.setProfile(uploadImage.getImageUrl());
                ProfileDto profileDto = new ProfileDto(nickname, star, uploadImage.getImageUrl());
                memberRepository.save(member);

                return profileDto;
            } else {
                ProfileDto profileDto = new ProfileDto(nickname, star);
                return profileDto;
            }
        }

        // password ??????
        public String changepass (UserDetailsImpl userDetails, PasswordChangeDto passwordChangeDto){
            Long userId = userDetails.getMember().getId();
            Member member = memberRepository.findById(userId).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            if (!passwordEncoder.matches(passwordChangeDto.getPasswordCheck(),userDetails.getPassword()) && !passwordChangeDto.getNewPassword().equals(passwordChangeDto.getNewPasswordCheck())) {
                throw new CustomException(ErrorCode.PASSWORD_PASSWORDCHECK);
            } else {
                member.setPassword(passwordEncoder.encode(passwordChangeDto.getNewPassword()));
            }
            return "???????????? ?????? ??????";
        }

        public void join (MemberRequestDto requestDto){
            Member member = new Member(requestDto.getUsername(), passwordEncoder.encode(requestDto.getPassword()));
            memberRepository.save(member);
        }

        public SimpleReviewResponseDto simpleReview (Long memberId){
        // ?????? ????????? ?????? ??? ??????????????? ????????? ?????? ??????
        Member member = memberRepository.findById(memberId).orElseThrow(
                ()-> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );
            Long reviewCnt = reviewRepository.countByTo(member.getId());
            List<Review> review = reviewRepository.findByTo(member.getId());
            List<Integer> reviewNum = new ArrayList<>();
            Map<Integer, Integer> reviews = new LinkedHashMap<>();
            Map<Integer, Integer> sortedReview = new LinkedHashMap<>();


            for (int i = 0; i < reviewCnt; i++) {
                 List<Integer> nums = review.get(i).getNum();
                reviewNum.addAll(nums);
            }

            for (int i = 0; i < 16; i++) {
                int numCnt = Collections.frequency(reviewNum, i);
                reviews.put(i, numCnt);
            }

            List<Map.Entry<Integer, Integer>> highs =
                    reviews.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                            .collect(Collectors.toList());

                for (int i = 0; i < 6; i++) {
                    Map.Entry<Integer, Integer> high = highs.get(i);
                    sortedReview.put(high.getKey(), high.getValue());
                }

            return new SimpleReviewResponseDto(sortedReview);
        }

        public List<Review> Review (Long memberId){
            return reviewRepository.findAllById(memberId);
        }

        public MyPostInfoResponseDto mypostinformation (UserDetailsImpl userDetails,int page, int amount){
            // page ??????
            Sort.Direction direction = Sort.Direction.ASC;
            String sortby = "createdAt";
            Sort sort = Sort.by(direction, sortby);
            Pageable pageable = PageRequest.of(page, amount, sort);

            Member member = memberRepository.findById(userDetails.getMember().getId()).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
//        List<Board> boards = boardRepository.findByMemberAndBoardType(member, "information");
            Page<Board> boards = boardRepository.findByMemberAndBoardType(member, "information", pageable);
            List<MyListInfoResponseDto> boardInfo = new ArrayList<>();
            for (Board board : boards) {
                MyListMemberResponseDto myListMemberResponseDto = new MyListMemberResponseDto(board.getMember().getUsername(),
                        board.getMember().getNickname(),
                        board.getMember().getProfile());
                MyListInfoResponseDto responseDto = new MyListInfoResponseDto(board.getId(),
                        board.getBoardType(),
                        board.getCategory().getCategory(),
                        board.getTitle(),
                        board.getContent(),
                        board.getLikeCount(),
                        board.getViewCount(),
                        board.getCommentCount(),
                        board.getBoardimage(),
                        board.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        board.getModifiedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        myListMemberResponseDto);
                boardInfo.add(responseDto);
            }
            Long informationCount = boardRepository.countByMemberAndAndBoardType(member, "information");
            return new MyPostInfoResponseDto(informationCount, boardInfo);
        }


        public MypostResponseDto mypostmatching (UserDetailsImpl userDetails,int page, int amount){
            // page ??????
            Sort.Direction direction = Sort.Direction.ASC;
            String sortby = "createdAt";
            Sort sort = Sort.by(direction, sortby);
            Pageable pageable = PageRequest.of(page, amount, sort);

            Member member = memberRepository.findById(userDetails.getMember().getId()).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
//        List<Board> boards = boardRepository.findByMemberAndBoardType(member, "matching");
            Page<Board> boards = boardRepository.findByMemberAndBoardType(member, "matching", pageable);
            List<MyListResponseDto> boardInfo = new ArrayList<>();
            for (Board board : boards) {
                MyListMemberResponseDto myListMemberResponseDto = new MyListMemberResponseDto(board.getMember().getUsername(),
                        board.getMember().getNickname(),
                        board.getMember().getProfile());
                MyListResponseDto responseDto = new MyListResponseDto(board.getId(),
                        board.getBoardType(),
                        board.getCategory().getCategory(),
                        board.getTitle(),
                        board.getContent(),
                        board.getEndDateAt(),
                        board.getLikeCount(),
                        board.getViewCount(),
                        board.getCommentCount(),
                        board.getCity().getCtpKorNmAbbreviation(),
                        board.getGu().getSigKorNm(),
                        board.getLat(),
                        board.getLng(),
                        board.getBoardimage(),
                        board.getMaxEntry(),
                        board.getCurrentEntry(),
                        board.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        board.getModifiedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        myListMemberResponseDto);
                boardInfo.add(responseDto);
            }
            Long matchingCount = boardRepository.countByMemberAndAndBoardType(member, "matching");
            return new MypostResponseDto(matchingCount, boardInfo);
        }

        public MypostResponseDto myentry (UserDetailsImpl userDetails,int page, int amount){
            // page ??????
            Sort.Direction direction = Sort.Direction.ASC;
            String sortby = "createdAt";
            Sort sort = Sort.by(direction, sortby);
            Pageable pageable = PageRequest.of(page, amount, sort);

            Member member = memberRepository.findById(userDetails.getMember().getId()).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
//        List<Entry> entries = entryRepository.findByMember(member);
            Page<Entry> entries = entryRepository.findByMember(member, pageable);
            List<MyListResponseDto> boardInfo = new ArrayList<>();
            for (Entry entry : entries) {
                MyListMemberResponseDto myListMemberResponseDto = new MyListMemberResponseDto(entry.getBoard().getMember().getUsername(),
                        entry.getBoard().getMember().getNickname(),
                        entry.getBoard().getMember().getProfile());
                MyListResponseDto responseDto = new MyListResponseDto(
                        entry.getBoard().getId(),
                        entry.getBoard().getBoardType(),
                        entry.getBoard().getCategory().getCategory(),
                        entry.getBoard().getTitle(),
                        entry.getBoard().getContent(),
                        entry.getBoard().getEndDateAt(),
                        entry.getBoard().getLikeCount(),
                        entry.getBoard().getViewCount(),
                        entry.getBoard().getCommentCount(),
                        entry.getBoard().getCity().getCtpKorNmAbbreviation(),
                        entry.getBoard().getGu().getSigKorNm(),
                        entry.getBoard().getLat(),
                        entry.getBoard().getLng(),
                        entry.getBoard().getBoardimage(),
                        entry.getBoard().getMaxEntry(),
                        entry.getBoard().getCurrentEntry(),
                        entry.getBoard().getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        entry.getBoard().getModifiedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        myListMemberResponseDto);
                boardInfo.add(responseDto);
            }
            Long totalCount = entryRepository.countByMember(member);
            return new MypostResponseDto(totalCount, boardInfo);
        }

        public MypostCommentResponseDto mycommentinformation (UserDetailsImpl userDetails,int page, int amount){
            // page ??????
            Sort.Direction direction = Sort.Direction.ASC;
            String sortby = "createdAt";
            Sort sort = Sort.by(direction, sortby);
            Pageable pageable = PageRequest.of(page, amount, sort);

            Member member = memberRepository.findById(userDetails.getMember().getId()).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            List<MyCommentResponseDto> commentList = new ArrayList<>();
//        List<Comment> comments = commentRepository.findAllByMember(member);
            Page<Comment> comments = commentRepository.findAllByMemberAndBoardType(member, "information", pageable);
            for (Comment comment : comments) {
                MyListMemberResponseDto myListMemberResponseDto = new MyListMemberResponseDto(comment.getMember().getUsername(),
                        comment.getMember().getNickname(),
                        comment.getMember().getProfile());
                MyCommentResponseDto responseDto = new MyCommentResponseDto(
                        comment.getId(),
                        comment.getBoard().getTitle(),
                        comment.getBoard().getId(),
                        comment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        comment.getModifiedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        comment.getComment(),
                        comment.getBoardType(),
                        myListMemberResponseDto);
                commentList.add(responseDto);
            }
            Long informationCount = commentRepository.countByMemberAndBoardType(member, "information");
            return new MypostCommentResponseDto(informationCount, commentList);
        }

        public MypostCommentResponseDto mycommentmatching (UserDetailsImpl userDetails,int page, int amount){
            // page ??????
            Sort.Direction direction = Sort.Direction.ASC;
            String sortby = "createdAt";
            Sort sort = Sort.by(direction, sortby);
            Pageable pageable = PageRequest.of(page, amount, sort);

            Member member = memberRepository.findById(userDetails.getMember().getId()).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
            List<MyCommentResponseDto> commentList = new ArrayList<>();
//        List<Comment> comments = commentRepository.findAllByMember(member);
            Page<Comment> comments = commentRepository.findAllByMemberAndBoardType(member, "matching", pageable);
            for (Comment comment : comments) {
                MyListMemberResponseDto myListMemberResponseDto = new MyListMemberResponseDto(comment.getMember().getUsername(),
                        comment.getMember().getNickname(),
                        comment.getMember().getProfile());
                MyCommentResponseDto responseDto = new MyCommentResponseDto(
                        comment.getId(),
                        comment.getBoard().getTitle(),
                        comment.getBoard().getId(),
                        comment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        comment.getModifiedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")),
                        comment.getComment(),
                        comment.getBoardType(),
                        myListMemberResponseDto);
                commentList.add(responseDto);
            }
            Long matchingCount = commentRepository.countByMemberAndBoardType(member, "matching");
            return new MypostCommentResponseDto(matchingCount, commentList);
        }


        // ????????? ??????????????? ?????? ??????
        public Member getMember (String nickname){
            return memberRepository.findByNickname(nickname).orElseThrow(
                    () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
            );
        }

        public void logout (UserDetailsImpl userDetails){
            redisUtil.deleteData(userDetails.getUsername() + JwtProperties.HEADER_REFRESH);
        }
    }
