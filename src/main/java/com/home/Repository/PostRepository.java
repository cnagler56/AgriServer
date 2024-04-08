package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.home.Domain.Post;
 
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
	
//	@Query("SELECT userId FROM posts INNER JOIN users ON posts.userId = users.id WHERE posts.title = {title} AND users.state = {state}")

	Post findByIdposts(Long idposts);	
	List<Post> findByState(String state);
}
