package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.SupplyDemand;

@Repository
public interface SupplyDemandRepository extends JpaRepository<SupplyDemand, Long> {
	List<SupplyDemand> findByCommodityOrderByRegionAscSeqAsc(String commodity);
	long deleteByCommodity(String commodity);
	long countByCommodity(String commodity);
}
