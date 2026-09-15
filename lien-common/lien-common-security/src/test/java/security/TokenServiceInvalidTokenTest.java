package security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.RedisService;
import service.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TokenServiceInvalidTokenTest {

    @Mock
    private RedisService redisService;

    @InjectMocks
    private TokenService tokenService;

    @Test
    void getUserInfoWithToken_whenTokenIsMalformed_shouldReturnNull() {
        assertThat(tokenService.getUserInfoWithToken("invalid-token")).isNull();

        verifyNoInteractions(redisService);
    }
}
