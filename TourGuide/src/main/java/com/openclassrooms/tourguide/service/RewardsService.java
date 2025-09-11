package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;

import gpsUtil.GpsUtil;
import org.springframework.stereotype.Service;

import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import com.openclassrooms.tourguide.model.User;
import com.openclassrooms.tourguide.model.UserReward;
import rewardCentral.RewardCentral;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
    private final int defaultProximityBuffer = 10;// proximity in miles
	private final ExecutorService executor = Executors.newFixedThreadPool(200);
	private final Semaphore semaphore = new Semaphore(75);
	private int proximityBuffer = defaultProximityBuffer;
    private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private List<Attraction> cachedAttractions;
	private final Map<String, CompletableFuture<Integer>> rewardsCache = new ConcurrentHashMap<>();

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	public void calculateRewards(User user) {
		try {
			semaphore.acquire(); // bloque si trop de tâches en parallèle
			executor.submit(() -> {
				try {
					processRewards(user);
				} finally {
					semaphore.release();
				}
			});
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void processRewards(User user) {
		List<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = getAttractions();

		List<CompletableFuture<Void>> futures = userLocations.stream()
				.flatMap(visitedLocation ->
						attractions.stream()
								.filter(attraction -> checkAttractionName(user, attraction) && nearAttraction(visitedLocation, attraction))
								.map(attraction ->
										getRewardPointsAsync(attraction, user)
												.thenAccept(rewardPoints ->
														user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints)))
								)
				).toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return executor.awaitTermination(timeout, unit);
	}
	public void awaitCompletion() throws InterruptedException {
		executor.shutdown();
		awaitTermination(1, TimeUnit.MINUTES);
	}

	private List<Attraction> getAttractions(){
		if (cachedAttractions == null) cachedAttractions = gpsUtil.getAttractions();
		return cachedAttractions;
	}

	private boolean checkAttractionName(User user, Attraction attraction){
		return user.getUserRewards().parallelStream()
				.noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));
	}
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        int attractionProximityRange = 200;
        return !(getDistance(attraction, location) > attractionProximityRange);
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return !(getDistance(attraction, visitedLocation.location) > proximityBuffer);
	}
	
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	private CompletableFuture<Integer> getRewardPointsAsync(Attraction attraction, User user) {
		String key = attraction.attractionId + "-" + user.getUserId();
		return rewardsCache.computeIfAbsent(key, k ->
				CompletableFuture.supplyAsync(() ->
						rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId()), executor)
		);
	}

	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);
		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));
		double nauticalMiles = 60 * Math.toDegrees(angle);
        return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}
}
