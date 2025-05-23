package com.example.tomyongji.my.service;

import static com.example.tomyongji.validation.ErrorMsg.EXISTING_USER;
import static com.example.tomyongji.validation.ErrorMsg.MISMATCHED_USER;
import static com.example.tomyongji.validation.ErrorMsg.NOT_FOUND_MEMBER;
import static com.example.tomyongji.validation.ErrorMsg.NOT_FOUND_STUDENT_CLUB;
import static com.example.tomyongji.validation.ErrorMsg.NOT_FOUND_USER;
import static com.example.tomyongji.validation.ErrorMsg.NO_AUTHORIZATION_BELONGING;
import static com.example.tomyongji.validation.ErrorMsg.NO_AUTHORIZATION_ROLE;
import static com.example.tomyongji.validation.ErrorMsg.NO_AUTHORIZATION_USER;

import com.example.tomyongji.admin.dto.MemberDto;
import com.example.tomyongji.admin.entity.Member;
import com.example.tomyongji.admin.mapper.AdminMapper;
import com.example.tomyongji.admin.repository.MemberRepository;
import com.example.tomyongji.admin.service.AdminService;
import com.example.tomyongji.auth.entity.User;
import com.example.tomyongji.auth.repository.ClubVerificationRepository;
import com.example.tomyongji.auth.repository.EmailVerificationRepository;
import com.example.tomyongji.auth.repository.UserRepository;
import com.example.tomyongji.auth.service.CustomUserDetails;
import com.example.tomyongji.my.dto.MemberRequestDto;
import com.example.tomyongji.my.dto.MyDto;
import com.example.tomyongji.my.dto.SaveMemberDto;
import com.example.tomyongji.my.mapper.MyMapper;
import com.example.tomyongji.receipt.entity.StudentClub;
import com.example.tomyongji.receipt.repository.StudentClubRepository;
import com.example.tomyongji.validation.CustomException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final ClubVerificationRepository clubVerificationRepository;
    private final MyMapper myMapper;

    // mapper 사용 추천
    public MyDto getMyInfo(Long id, UserDetails currentUser) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new CustomException(NOT_FOUND_USER, 400));
        checkMismatchedUser(user, currentUser); //타인의 정보 열람을 방지
        if (user.getStudentClub() == null) {
            throw new CustomException(NOT_FOUND_STUDENT_CLUB, 400);
        }
        return myMapper.toMyDto(user);
    }



    public List<MemberDto> getMembers(Long id, UserDetails currentUser) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new CustomException(NOT_FOUND_USER, 400));
        checkMismatchedUser(user, currentUser); //id의 유저와 접속한 유저의 정보 매칭
        StudentClub studentClub = user.getStudentClub();
        List<Member> members = memberRepository.findByStudentClub(studentClub);
        List<MemberDto> memberDtos = new ArrayList<>();
        for (Member member : members) {
            memberDtos.add(myMapper.toMemberDto(member));
        }
        return memberDtos;
    }

    public void saveMember(SaveMemberDto memberDto, UserDetails currentUser) {
        User user = userRepository.findById(memberDto.getId())
            .orElseThrow(() -> new CustomException(NOT_FOUND_USER, 400));
        checkMismatchedUser(user, currentUser);
        if (memberRepository.existsByStudentNum(memberDto.getStudentNum())) {
            throw new CustomException(EXISTING_USER, 400);  // 중복 학번 예외 처리
        }
        StudentClub studentClub = Optional.ofNullable(user.getStudentClub())
            .orElseThrow(() -> new CustomException(NOT_FOUND_STUDENT_CLUB, 400));
        //멤버 생성 후
        //받은 dto의 정보를 멤버에 삽입
        //멤버의 학생회 정보를 따로 삽입
        //해당 멤버를 레포지터리에 저장
        Member member = myMapper.toMemberEntity(memberDto);
        member.setStudentClub(studentClub);
        memberRepository.save(member);
    }

    @Transactional
    public MemberDto deleteMember(String deletedStudentNum, UserDetails currentUser) {

        Member member = memberRepository.findByStudentNum(deletedStudentNum)
            .orElseThrow(() -> new CustomException(NOT_FOUND_MEMBER, 400));

        //삭제할 멤버의 소속과 접속한 유저의 소속 비교
        User connectedUser = userRepository.findByUserId(currentUser.getUsername())
            .orElseThrow(() -> new CustomException(NOT_FOUND_USER, 400));
        if (!member.getStudentClub().equals(connectedUser.getStudentClub())) {
            throw new CustomException(NO_AUTHORIZATION_BELONGING, 400);
        }

        MemberDto memberDto = myMapper.toMemberDto(member); //삭제된 멤버 정보를 보여주기 위한 반환값

        if (!clubVerificationRepository.findByStudentNum(deletedStudentNum).isEmpty()) {
            clubVerificationRepository.deleteByStudentNum(deletedStudentNum);
        }
        //멤버 등록을 해도 유저가 없을 수 있음
        Optional<User> user = Optional.ofNullable(
            userRepository.findByStudentNum(deletedStudentNum));
        //유저가 있다면 유저의 메일과 유저를 삭제
        if (user.isPresent()) {
            emailVerificationRepository.deleteByEmail(user.get().getEmail());
            userRepository.delete(user.get());
        }
        //등록된 멤버 정보도 삭제
        memberRepository.delete(member);
        return memberDto;
    }
    private void checkMismatchedUser(User user, UserDetails currentUser) {
        User compareUser = userRepository.findByUserId(currentUser.getUsername())
            .orElseThrow(() -> new CustomException(NO_AUTHORIZATION_USER, 400));
        if (!Objects.equals(compareUser.getId(), user.getId())) { //로그인한 유저와 매개변수의 유저가 같은지 확인
            throw new CustomException(MISMATCHED_USER, 400);
        }
    }
}