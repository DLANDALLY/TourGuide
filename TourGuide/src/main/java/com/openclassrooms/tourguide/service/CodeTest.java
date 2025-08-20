package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CodeTest {
//    public static void main(String[] args) throws InterruptedException {
//        // Simulons 10 000 utilisateurs
//        List<String> utilisateurs = IntStream.range(0, 10000)
//                .mapToObj(i -> "user" + i)
//                .collect(Collectors.toList());
//
//        // Création d'un ExecutorService avec un grand pool de threads
//        ExecutorService executor = Executors.newFixedThreadPool(100); // Ajuste selon ta machine
//
//        // Liste des futures
//        List<CompletableFuture<Void>> futures = utilisateurs.stream()
//                .map(user -> CompletableFuture
//                        .supplyAsync(() -> traiterUtilisateur(user), executor)
//                        .thenAcceptAsync(result -> {
//                            // Callback pour chaque utilisateur
//                            System.out.println("Résultat pour " + user + ": " + result);
//                        }, executor)
//                )
//                .collect(Collectors.toList());
//
//        // Optionnel : attendre que tout soit terminé
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//
//        // Libérer les ressources
//        executor.shutdown();
//    }
//
//    private static String traiterUtilisateur(String user) {
//        // Simule une tâche longue
//        try {
//            Thread.sleep(100); // ex : appel réseau, calcul intensif...
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//        return "Traitement terminé pour " + user;
//    }
}

