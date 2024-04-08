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
    
    public Post getfullPost(Long idposts) {
        return this.postRepository.findByIdposts(idposts);
    }
    
    public Iterable <Post> getPostsbylocation(String state) {
    	if(state == "All" || state == null) {
    		return this.postRepository.findAll();
    	}
    	return (Iterable<Post>) this.postRepository.findByState(state);
    }
    

    
    public void addPost(Post post) {
    	this.postRepository.save(post);
    }
}
