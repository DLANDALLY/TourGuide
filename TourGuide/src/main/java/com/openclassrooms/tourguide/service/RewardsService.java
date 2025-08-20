package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
	// proximity in miles
    private final int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
    private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private List<Attraction> cachedAttractions;

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

	//Method original
	public void calculateRewardsOriginal(User user) {
		List<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = getAttractions();

		userLocations.forEach(visitedLocation -> {
			 attractions.stream()
					.filter(attraction -> checkAttractionName(user, attraction) && nearAttraction(visitedLocation, attraction))
					.forEach(attraction -> {
						int rewardPoints = getRewardPoints(attraction, user);
						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
					});
		});
    }

	//Method test
	public void calculateRewards(User user) {
		ExecutorService executor = Executors.newFixedThreadPool(100); // Ajuste selon ta machine

		List<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = getAttractions();

		//Boucle userLocation
		userLocations.forEach(visitedLocation -> {
			attractions.stream()
					.filter(attraction -> checkAttractionName(user, attraction) && nearAttraction(visitedLocation, attraction))
					.forEach(attraction -> {
						int rewardPoints = getRewardPoints(attraction, user);
						user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
					});
		});


		// Liste des futures
//		List<CompletableFuture<Void>> futures = utilisateurs.stream()
//				.map(user -> CompletableFuture
//						.supplyAsync(() -> traiterUtilisateur(user), executor)
//						.thenAcceptAsync(result -> {
//							// Callback pour chaque utilisateur
//							System.out.println("Résultat pour " + user + ": " + result);
//						}, executor)
//				)
//				.collect(Collectors.toList());
//
//		// Optionnel : attendre que tout soit terminé
//		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// Libérer les ressources
		executor.shutdown();
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
