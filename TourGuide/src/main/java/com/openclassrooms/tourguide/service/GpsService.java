package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.model.User;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class GpsService {
    // Cache avec expiration (coordonnées par utilisateur)
    private final Map<UUID, CachedLocation> locationCache = new ConcurrentHashMap<>();

    // Executor partagé
    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    // Sémaphore pour limiter le nombre de requêtes simultanées au service GPS
    private final Semaphore semaphore = new Semaphore(15); // max 5 appels en parallèle

    // Durée de validité du cache (exemple : 5 minutes)
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    public CompletableFuture<VisitedLocation> getUserLocation(User user) {
        UUID userId = user.getUserId();

        // Vérifie le cache
        CachedLocation cached = locationCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.location);
        }

        // Sinon appel asynchrone
        return CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire(); // attend un slot libre
                // --- Appel réel GPS ---
                VisitedLocation loc = callGpsApi(userId);
                // --- Cache ---
                locationCache.put(userId, new CachedLocation(loc));
                return loc;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while calling GPS API", e);
            } finally {
                semaphore.release();
            }
        }, executor);
    }

    // Simulation d'appel à l’API GPS
    private VisitedLocation callGpsApi(UUID userId) {
        try {
            Thread.sleep(200); // latence réseau simulée
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new VisitedLocation(userId, new Location(Math.random()*100, Math.random()*100), new Date());
    }

    // Objet cache
    private static class CachedLocation {
        final VisitedLocation location;
        final long timestamp;

        CachedLocation(VisitedLocation loc) {
            this.location = loc;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
