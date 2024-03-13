package com.home.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.home.Domain.Beans;
import com.home.Domain.CornYields;
import com.home.Repository.CornRepository;
import com.home.Repository.GrainRepository;


@Service
public class GrainService {

	private final GrainRepository grainRepository;
	private final CornRepository repo;
	
	public GrainService(GrainRepository grainRepository, CornRepository repo) {
		this.grainRepository = grainRepository;
		this.repo = repo;
	}

	
	
    public List<Beans> getBeans() {
        return this.grainRepository.findAll();
    }
    
    public List<CornYields> getCorn() {
        return this.repo.findAll();
    }
}
