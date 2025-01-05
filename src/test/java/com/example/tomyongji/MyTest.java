package com.example.tomyongji;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tomyongji.admin.dto.MemberDto;
import com.example.tomyongji.admin.entity.Member;
import com.example.tomyongji.admin.repository.MemberRepository;
import com.example.tomyongji.auth.entity.User;
import com.example.tomyongji.auth.repository.UserRepository;
import com.example.tomyongji.my.dto.MyDto;
import com.example.tomyongji.my.dto.SaveMemberDto;
import com.example.tomyongji.my.mapper.MyMapper;
import com.example.tomyongji.my.service.MyService;
import com.example.tomyongji.receipt.entity.StudentClub;
import com.example.tomyongji.receipt.repository.StudentClubRepository;
import com.example.tomyongji.validation.CustomException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MyMapper myMapper;

    @Autowired
    private MyService myService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentClubRepository studentClubRepository;
    private User user;


    @BeforeEach
    void setup() {
        //StudentClub 저장
        StudentClub studentClub = StudentClub.builder()
            .studentClubName("융합소프트웨어학부")
            .Balance(100000)
            .build();
        studentClub = studentClubRepository.saveAndFlush(studentClub); //저장 후 반환된 객체 사용

        //User 저장: 융합소프트웨어학부 학생회장
        user = User.builder()
            .userId("testUser")
            .name("test name")
            .studentNum("60000000")
            .collegeName("ICT 융합대학")
            .email("test@example.com")
            .password("password123")
            .role("PRESIDENT")
            .studentClub(studentClub) //저장된 StudentClub 설정
            .build();
        userRepository.saveAndFlush(user);


        //테스트
        Optional<User> findingUser = userRepository.findById(user.getId());
        if (findingUser.isEmpty()) {
            System.out.println("User not found after save!");
        } else {
            System.out.println("유저 명: " + findingUser.get().getName());
        }
        System.out.println("유저 학과 명: " + user.getStudentClub().getStudentClubName());
    }

    @Test
    @DisplayName("유저 정보 조회 흐름 테스트")
    void testGetMyInfoFlow() throws Exception {
        //Given
        Long id = user.getId();
        assertThat(id).isNotNull();

        //When, Then
        mockMvc.perform(get("/api/my/{id}", id)  // URL 템플릿 사용
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.statusMessage").value("내 정보 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.studentNum").value("60000000"));

        MyDto myDto = myService.getMyInfo(user.getId());
        assertThat(myDto).isNotNull();
        assertThat(myDto.getStudentNum()).isEqualTo("60000000");
    }


    @Test
    @DisplayName("멤버 정보 조회 흐름 테스트")
    void testGetMembersFlow() throws Exception {
        //Given
        Long id = user.getId();

        Member member1 = Member.builder()
            .studentNum("60000001")
            .name("test member1")
            .studentClub(user.getStudentClub())
            .build();
        Member member2 = Member.builder()
            .studentNum("60000002")
            .name("test member2")
            .studentClub(user.getStudentClub())
            .build();

        memberRepository.save(member1);
        memberRepository.save(member2);

        //When, Then
        mockMvc.perform(get("/api/my/members/{id}", id)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.statusMessage").value("소속 부원 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data[0].studentNum").value("60000001"))
            .andExpect(jsonPath("$.data[1].studentNum").value("60000002"));

        List<MemberDto> members = myService.getMembers(id);
        assertThat(members.get(0).getStudentNum()).isEqualTo(member1.getStudentNum());
        assertThat(members.get(1).getStudentNum()).isEqualTo(member2.getStudentNum());
    }

    @Test
    @DisplayName("멤버 저장 흐름 테스트")
    void testSaveMemberFlow() throws Exception {
        //Given
        SaveMemberDto saveMemberDto = SaveMemberDto.builder()
            .id(user.getId())
            .studentNum("60000003")
            .name("test name3")
            .build();

        //When, Then
        mockMvc.perform(post("/api/my/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saveMemberDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.statusMessage").value("소속 부원 정보 저장에 성공했습니다."));

        Member savedMember = memberRepository.findByStudentNum("60000003").orElse(null);
        assertThat(savedMember).isNotNull();
        assertThat(savedMember.getName()).isEqualTo("test name3");
    }

    @Test
    @DisplayName("멤버 삭제 흐름 테스트")
    void testDeleteMemberFlow() throws Exception {
        // Given: 삭제할 멤버 데이터 준비
        Member deleteMember = Member.builder()
            .studentNum("60000004")
            .name("test name4")
            .studentClub(user.getStudentClub())
            .build();
        memberRepository.save(deleteMember);

        String deletedStudentNum = deleteMember.getStudentNum();

        // When & Then: 컨트롤러 -> 서비스 -> 레포지터리 -> 엔티티 흐름 테스트
        mockMvc.perform(delete("/api/my/members/{deletedStudentNum}", deletedStudentNum)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.statusMessage").value("소속 부원 삭제에 성공했습니다."));

        Member deletedMember = memberRepository.findByStudentNum(deletedStudentNum).orElse(null);
        assertThat(deletedMember).isNull();
    }

}
