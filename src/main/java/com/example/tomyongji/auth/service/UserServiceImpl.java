package com.example.tomyongji.auth.service;

import com.example.tomyongji.admin.entity.Member;
import com.example.tomyongji.admin.entity.President;
import com.example.tomyongji.admin.repository.MemberRepository;
import com.example.tomyongji.admin.repository.PresidentRepository;
import com.example.tomyongji.auth.dto.LoginRequestDto;
import com.example.tomyongji.auth.dto.UserRequestDto;
import com.example.tomyongji.auth.entity.ClubVerification;
import com.example.tomyongji.auth.entity.EmailVerification;
import com.example.tomyongji.auth.entity.User;
import com.example.tomyongji.auth.jwt.JwtProvider;
import com.example.tomyongji.auth.jwt.JwtToken;
import com.example.tomyongji.auth.mapper.UserMapper;
import com.example.tomyongji.auth.repository.ClubVerificationRepository;
import com.example.tomyongji.auth.repository.EmailVerificationRepository;
import com.example.tomyongji.auth.repository.UserRepository;
import com.example.tomyongji.receipt.entity.College;
import com.example.tomyongji.receipt.entity.StudentClub;
import com.example.tomyongji.receipt.repository.CollegeRepository;
import com.example.tomyongji.receipt.repository.StudentClubRepository;
import com.example.tomyongji.validation.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.tomyongji.validation.ErrorMsg.*;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final MemberRepository memberInfoRepository;
    private final CollegeRepository collegeRepository;
    private final PresidentRepository presidentInfoRepository;
    private final StudentClubRepository studentClubRepository;
    private final ClubVerificationRepository clubVerificationRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtProvider jwtProvider;
    private final UserMapper userMapper;
    @Override
    public Long signUp(UserRequestDto dto) {
        // 각 학생회, 대학이 존재하는지
        College college = collegeRepository.findByCollegeName(dto.getCollegeName())
                .orElseThrow(() -> new CustomException(NOT_FOUND_COLLEGE, 400));
        StudentClub studentClub = studentClubRepository.findById(dto.getStudentClubId())
                .orElseThrow(()-> new CustomException(NOT_FOUND_STUDENT_CLUB,400));
        // 대학 안에 학생회가 존재하는지
        if(college.getId()!=studentClub.getCollege().getId()){
            throw new CustomException(NOT_HAVE_STUDENT_CLUB,400);
        }
        // userID가 겹치지 않는지
        Optional<User> validUser = userRepository.findByUserId(dto.getUserId());
        if(!validUser.isEmpty()){
            throw new CustomException(EXISTING_USER,400);
        }
        // email 인증이 되었는지
        EmailVerification emailVerification = emailVerificationRepository.findByEmail(dto.getEmail())
                .orElseThrow(()->new CustomException(NOT_VERIFY_EMAIL,400));
        // 소속 인증이 되었는지
        ClubVerification clubVerification = clubVerificationRepository.findByStudentNum(dto.getStudentNum())
                .orElseThrow(()-> new CustomException(NOT_VERIFY_CLUB,400));
        //Dto->Entity
        User user = userMapper.toUser(dto,studentClub);
        // 비밀번호 해시 처리
        user.setPassword(encoder.encode(user.getPassword()));
        User response = userRepository.save(user);
        // user foreign key mapping 해주기
        emailVerification.setUser(user);
        clubVerification.setUser(user);
        emailVerificationRepository.save(emailVerification);
        clubVerificationRepository.save(clubVerification);

        return response.getId();
    }

    @Override
    public Boolean checkUserIdDuplicate(String userId) {
        return userRepository.existsByUserId(userId);
    }

    @Override
    public JwtToken login(LoginRequestDto dto) {
        String userId = dto.getUserId();
        String password = dto.getPassword();
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userId, password);
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 인증된 사용자 정보 가져오기
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        // JwtToken 생성
        JwtToken jwtToken = jwtProvider.generateToken(authentication, this.userRepository.findByUserId(dto.getUserId()).get().getId());
        return jwtToken;
    }

    @Override
    public String findUserIdByEmail(String email) {
        return this.userRepository.findByEmail(email).map(User::getUserId)
                .orElseThrow(() -> new CustomException(NOT_FOUND_USER_EMAIL,400));
    }


    @Override
    public Boolean verifyClub(Long clubId, String studentNum) { //학생회 아이디, 유저 학번

        StudentClub studentClub = this.studentClubRepository.findById(clubId)
                .orElseThrow(()-> new CustomException(NOT_FOUND_STUDENT_CLUB,400));

        President president =this.presidentInfoRepository.findByStudentNum(studentNum);
        if (president==null) { //회장의 학번이 아니라면
            Member member = this.memberInfoRepository.findByStudentNum(studentNum)
                .orElseThrow(() -> new CustomException(NOT_FOUND_MEMBER, 400));
            ClubVerification clubVerification = ClubVerification.builder()
                    .studentNum(studentNum)
                    .verificatedAt(LocalDateTime.now())
                    .build();

            clubVerificationRepository.save(clubVerification);
            return true;
        }else {
            StudentClub userClub = studentClubRepository.findByPresident(president)
                    .orElseThrow(()-> new CustomException(NOT_FOUND_STUDENT_CLUB,400));
            ClubVerification clubVerification = ClubVerification.builder()
                    .studentNum(studentNum)
                    .verificatedAt(LocalDateTime.now())
                    .build();

            clubVerificationRepository.save(clubVerification);
            return true;
        }
    }



}
