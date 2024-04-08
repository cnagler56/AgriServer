package com.home.Service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import com.home.Domain.Beans;
import com.home.Domain.CornGuess;
import com.home.Domain.CornYields;
import com.home.Repository.CornRepository;
import com.home.Repository.CornYieldRepository;
import com.home.Repository.GrainRepository;


@Service
public class GrainService {

	private final GrainRepository grainRepository;
	private final CornRepository repo;
	private final CornYieldRepository cornrepo;
	
	public GrainService(GrainRepository grainRepository, CornRepository repo, CornYieldRepository cornrepo) {
		this.grainRepository = grainRepository;
		this.repo = repo;
		this.cornrepo = cornrepo;
	}

	 public ResponseEntity<String> addCornYield(CornGuess cornGuess) {
	        try {
	         System.out.println(cornGuess);
	        	cornrepo.save(cornGuess); // Save the received CornYield object to the database
	            return new ResponseEntity<>("Data inserted successfully", HttpStatus.OK);
	        } catch (Exception e) {
	            e.printStackTrace();
	            return new ResponseEntity<>("Error inserting data", HttpStatus.INTERNAL_SERVER_ERROR);
	        }
	    }
	
    public List<Beans> getBeans() {
        return this.grainRepository.findAll();
    }
    
    public List<CornYields> getCorn() {
        return this.repo.findAll();
    }
}
