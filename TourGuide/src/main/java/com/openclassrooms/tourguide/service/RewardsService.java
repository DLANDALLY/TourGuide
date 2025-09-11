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

	/**
	 * Soumet une tâche asynchrone pour calculer les récompenses d'un utilisateur.
	 * L'utilisation d'un sémaphore permet de limiter le nombre de calculs
	 * exécutés en parallèle afin de ne pas surcharger le système
	 * @param user calcule les récompenses de l'utilisateur
	 */
	public void calculateRewards(User user) {
		try {
			semaphore.acquire(); // bloque si trop de tâches en parallèle
			executor.submit(() -> {
				try { processRewards(user); }
				finally { semaphore.release(); }
			});
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Calcule de manière asynchrone les récompenses d'un utilisateur
	 * en fonction de ses visites et des attractions disponibles
	 *
	 * Pour chaque localisation visitée par l'utilisateur on recherche
	 * les attractions proches. Lorsqu'une attraction correspond
	 * un calcul de points est lancé de façon asynchrone puis
	 * ajouté à la liste des récompenses de l'utilisateur
	 *
	 * L'exécution attend que l'ensemble des calculs de points
	 * soient terminés avant de se terminer
	 *
	 * @param user l'utilisateur dont on veut traiter les récompenses
	 */
	private void processRewards(User user) {
		List<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = getAttractions();

		List<CompletableFuture<Void>> futures = userLocations.stream()
				.flatMap(visitedLocation ->
						attractions.stream()
								.filter(attraction -> checkAttractionName(user, attraction) && nearAttraction(visitedLocation, attraction))
								.map(attraction -> getRewardPointsAsync(attraction, user)
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

	/**
	 * Récupère de manière asynchrone les points de récompense pour une attraction donnée et un utilisateur.
	 * Les résultats sont mis en cache afin d'éviter des appels redondants
	 * Si la combinaison {@code (attraction, user)} existe déjà dans le cache, le calcul n'est pas relancé.
	 *
	 * @param attraction l'attraction pour laquelle calculer les points de récompense
	 * @param user       l'utilisateur concerné par le calcul des points
	 * @return un {@link CompletableFuture} fournissant le nombre de points de récompense associés
	 */
	private CompletableFuture<Integer> getRewardPointsAsync(Attraction attraction, User user) {
		String key = attraction.attractionId + "-" + user.getUserId();
		return rewardsCache.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() ->
				rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId()), executor)
		);
	}

	/**
	 * Calcule la distance en miles entre deux coordonnées géographiques.
	 *
	 * @param loc1 première localisation
	 * @param loc2 deuxième localisation
	 * @return la distance entre les deux points en miles
	 */
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
