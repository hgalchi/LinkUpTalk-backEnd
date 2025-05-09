package LinkUpTalk.auth;

import LinkUpTalk.annotation.IntegrateTest;
import LinkUpTalk.auth.domain.Repository.RefreshRepository;
import LinkUpTalk.config.IntegrationTest;
import LinkUpTalk.user.infrastructor.JpaUserRepository;
import LinkUpTalk.util.TestUtil;
import LinkUpTalk.auth.domain.Refresh;
import LinkUpTalk.auth.domain.constant.TokenType;
import LinkUpTalk.user.presentation.dto.UserCreateReqDto;
import LinkUpTalk.common.util.JwtUtil;
import LinkUpTalk.user.domain.User;
import LinkUpTalk.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AuthServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebApplicationContext ctx;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RefreshRepository refreshRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestUtil testUtil;

    final String USERNAME = "testAuthUser";
    final String PASSWORD = "AutUser1234";
    final String EMAIL = "testAuthUser@test.com";


    @BeforeEach
    public void setup(){
        mvc = MockMvcBuilders
                .webAppContextSetup(ctx)
                .apply(springSecurity())
                .alwaysDo(print())
                .build();
    }

    @IntegrateTest
    @DisplayName("회원가입 성공")
    public void register_suc() throws Exception {
        //given
        UserCreateReqDto dto = UserCreateReqDto.builder()
                .name(USERNAME)
                .password(PASSWORD)
                .email(EMAIL)
                .build();

        //when & then
        mvc.perform(post("/auth/signUp")
                        .content(mapper.writeValueAsString(dto))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @IntegrateTest
    @DisplayName("회원가입 실패_이메일 형식 오류")
    public void register_failWithValidation() throws Exception {
        //given
        String invalidEmail="testAuthUser";
        UserCreateReqDto dto = UserCreateReqDto.builder()
                .name(USERNAME)
                .password(PASSWORD)
                .email(invalidEmail)
                .build();

        //when & then
        mvc.perform(post("/auth/signUp")
                        .content(mapper.writeValueAsString(dto))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @IntegrateTest
    @DisplayName("회원가입 실패_중복된 이메일")
    public void register_failWithDuplicatedEmail() throws Exception {
        //given
        testUtil.registerUser(USERNAME,EMAIL,PASSWORD);
        UserCreateReqDto dto = UserCreateReqDto.builder()
                .name(USERNAME)
                .password(PASSWORD)
                .email(EMAIL)
                .build();

        //when & then
        mvc.perform(post("/auth/signUp")
                        .content(mapper.writeValueAsString(dto))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @IntegrateTest
    @DisplayName("로그인 성공")
    public void login_suc() throws Exception {
        //given
        testUtil.registerUser(USERNAME,EMAIL,PASSWORD);

        //when & then
        mvc.perform(formLogin().user(EMAIL).password(PASSWORD))
                .andExpect(status().isOk());
    }


    @IntegrateTest
    @DisplayName("로그인 실패_잘못된 아이디")
    public void login_failWithWrongID() throws Exception {
        //given
        testUtil.registerUser(USERNAME,EMAIL,PASSWORD);

        //when & then
        mvc.perform(formLogin().user(EMAIL+"Wrong").password(PASSWORD))
                .andExpect(status().isUnauthorized());


    }
    @IntegrateTest
    @DisplayName("사용자 탈퇴")
    @WithMockUser(username=EMAIL,roles = "CUSTOMER")
    public void deleteUser_suc() throws Exception {
        //given
        User user=testUtil.registerUser(USERNAME,EMAIL,PASSWORD);

        //when
        mvc.perform(delete("/auth/signOut/{userId}", user.getId()))
                .andExpect(status().isOk());
        User deleteUser = userRepository.findById(user.getId()).orElse(null);
        //then
        Assertions.assertThat(deleteUser).isNotNull();
        Assertions.assertThat(deleteUser.isDeleted()).isTrue();
    }

    @IntegrateTest
    @DisplayName("액세스 토큰 재발급 성공")
    public void issueRefresh_suc() throws Exception {
        // given
        User user = testUtil.registerUser(USERNAME,EMAIL,PASSWORD);
        String refreshToken = registerRefresh(user);

        // when
        MvcResult result = mvc.perform(post("/auth/reissue")
                        .cookie(new Cookie(TokenType.refreshToken.name(), refreshToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        //then
        String accessToken = result.getResponse().getHeader(TokenType.accessToken.name());
        assertThat(accessToken).isNotNull();
        assertThat(accessToken).isNotEmpty();

        Cookie refreshTokenCookie = result.getResponse().getCookie(TokenType.refreshToken.name());
        assertThat(refreshTokenCookie).isNotNull();
    }

    @IntegrateTest
    @DisplayName("액세스 토큰 재발급 실패_리플레시 토큰을 찾을 수 없음")
    public void issueRefresh_failWithExpiredRefresh() throws Exception {

        //when & then
        mvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }



    private String registerRefresh(User user){
        String refreshToken = jwtUtil.createToken(EMAIL, Collections.singletonList("ROLE_USER"), TokenType.refreshToken.name());

        Refresh refresh = Refresh.builder()
                .refresh(jwtUtil.resolveToken(refreshToken))
                .expiration(jwtUtil.getRefreshExpireTime())
                .user(user)
                .build();
        refreshRepository.save(refresh);
        return refreshToken;
    }

}
