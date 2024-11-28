package com.home.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import com.home.Domain.Token;

@Repository
public interface TokenRepository extends JpaRepository<Token, Integer> {
	
    @Query("""
select t from Token t inner join User u on t.user.id = u.id
where t.user.id = :userId and t.loggedOut = false
""")
    List<Token> findAllTokensByUser(Long userId);

    Optional<Token> findByToken(String token);
}
