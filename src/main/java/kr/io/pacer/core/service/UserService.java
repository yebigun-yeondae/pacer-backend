package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.SocialType;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User getMemberBySocialId(String socialId) {
        User user = userRepository.findBySocialId(socialId).orElse(null);
        return user;
    }

    public User createOauth(String socialId, String email, SocialType socialType) {
        User user = User.builder()
                .email(email)
                .socialType(socialType)
                .socialId(socialId)
                .build();
        userRepository.save(user);
        return user;
    }
}
