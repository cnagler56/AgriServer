package com.home.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.home.Domain.Post;
import com.home.Repository.PostRepository;

@Service
public class PostService {
	
	private final PostRepository postRepository;
	
	public PostService(PostRepository postRepository) {
		this.postRepository = postRepository;
	}

	
	
    public List<Post> getPosts() {
        return this.postRepository.findAll();
    }
    
//    public List<Post> getFiltered(String title, String state) {
//    	return this.postRepository.findByTitleandState(title, state);
//    }
    
    public void addPost(Post post) {
    	this.postRepository.save(post);
    }
}
