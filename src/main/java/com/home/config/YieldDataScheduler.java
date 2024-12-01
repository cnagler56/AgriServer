//package com.home.config;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import com.home.Service.YieldDataService;
//
//@Component
//public class YieldDataScheduler {
//	
//	@Autowired
//	private YieldDataService yieldDataService;
//	
////	@Scheduled(cron = "0 0 0 * * ?")
//	@Scheduled(cron = "0 10 * * *")
//	public void updateYieldData() {
//		yieldDataService.fetchAndStoreYieldData();
//	}
//
//}
