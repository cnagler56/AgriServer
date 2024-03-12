package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.Post;
 
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

}
