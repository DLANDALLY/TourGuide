package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.model.User;
import com.openclassrooms.tourguide.model.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	private final static int LIMIT_ATTRACTIONS = 5;
	// Executor pour le tracking parallèle contrôlé
	private final ExecutorService executor = Executors.newFixedThreadPool(200);
	private final ConcurrentHashMap<UUID, VisitedLocation> locationCache = new ConcurrentHashMap<>();

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		Locale.setDefault(Locale.US);

        logger.info("TestMode enabled");
        logger.debug("Initializing users");
        initializeInternalUsers();
        logger.debug("Finished initializing users");
        tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	//Version Original
//	public VisitedLocation getUserLocation(User user) {
//        return (!user.getVisitedLocations().isEmpty()) ? user.getLastVisitedLocation()
//                : trackUserLocation(user);
//	}
	//Nouvelle version version
	public VisitedLocation getUserLocation(User user) {
		return (!user.getVisitedLocations().isEmpty()) ? user.getLastVisitedLocation()
				: trackUserLocationWithCache(user);
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return new ArrayList<>(internalUserMap.values());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(UserReward::getRewardPoints).sum();
		List<Provider> providers = getTripPricer(user, cumulatativeRewardPoints);
		providers.addAll(getTripPricer(user, cumulatativeRewardPoints));

		user.setTripDeals(providers);
		return providers;
	}

	private List<Provider> getTripPricer(User user, int cumulatativeRewardPoints){
		return tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
	}

//	public VisitedLocation trackUserLocation(User user) {
//		VisitedLocation visitedLocation = this.gpsUtil.getUserLocation(user.getUserId());
//		user.addToVisitedLocations(visitedLocation);
//		//rewardsService.calculateRewards(user);
//		rewardsService.calculateRewardsOriginal(user);
//		return visitedLocation;
//	}
	//### Test
	public void trackAllUsers(List<User> allUsers) throws ExecutionException, InterruptedException {
		List<Future<?>> futures = new ArrayList<>();
		for (User user : allUsers) {
			futures.add(executor.submit(() -> trackUserLocationWithCache(user)));
		}
		// Attendre la fin de tous les tracking
		for (Future<?> f : futures) {
			f.get();
		}
	}
	public void trackUser(User user) throws ExecutionException, InterruptedException {
		List<Future<VisitedLocation>> futures = new ArrayList<>();
		futures.add(executor.submit(() -> trackUserLocationWithCache(user)));

		// Attendre la fin de tous les tracking
		for (Future<VisitedLocation> f : futures) {
			f.get();
		}
    }
	// Version trackUserLocation avec cache
	public VisitedLocation trackUserLocationWithCache(User user) {
		UUID userId = user.getUserId();

		// Vérifier si le cache contient une location récente
		VisitedLocation visitedLocation = locationCache.get(userId);
		if (visitedLocation == null || isCacheExpired(visitedLocation)) {
			// Pas dans le cache ou expiré → appel GPS
			visitedLocation = gpsUtil.getUserLocation(userId);
			locationCache.put(userId, visitedLocation);
		}
		// Ajouter dans l'historique utilisateur
		user.addToVisitedLocations(visitedLocation);
		// Calculer les récompenses
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}
	// Expiration simple : ici 1 minute
	private boolean isCacheExpired(VisitedLocation visitedLocation) {
		long now = System.currentTimeMillis();
		long elapsed = now - visitedLocation.timeVisited.getTime();
		return elapsed > TimeUnit.MINUTES.toMillis(1);
	}

	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
        List<Attraction> sortAttractionsByDistance = sortAttractionsByDistance(visitedLocation).stream()
				.limit(LIMIT_ATTRACTIONS).toList();
        return new ArrayList<>(sortAttractionsByDistance);
	}

	public List<Attraction> sortAttractionsByDistance(VisitedLocation visitedLocation) {
		return gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(attraction ->
						rewardsService.getDistance(attraction, visitedLocation.location)
				))
				.collect(Collectors.toList());
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() { tracker.stopTracking(); }
		});
	}

	/**********************************************************************************
	 *
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
        logger.debug("Created {} internal test users.", InternalTestHelper.getInternalUserNumber());
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
