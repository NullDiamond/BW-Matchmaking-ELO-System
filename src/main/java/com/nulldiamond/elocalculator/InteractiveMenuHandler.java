package com.nulldiamond.elocalculator;

import java.util.Scanner;

/**
 * Handles the interactive menu for the ELO Calculator application.
 */
public class InteractiveMenuHandler {

    private final Main mainApp;

    /**
     * Constructor.
     * @param mainApp the main application instance
     */
    public InteractiveMenuHandler(Main mainApp) {
        this.mainApp = mainApp;
    }

    /**
     * Runs the interactive terminal menu.
     */
    public void runInteractive() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Enter your choice: ");

            String choice = "";
            try {
                choice = scanner.nextLine().trim();
                System.out.println();

                switch (choice) {
                    case "1":
                        mainApp.run();
                        break;

                    case "2":
                        System.out.print("Enter player name: ");
                        String historyName = scanner.nextLine().trim();
                        if (!historyName.isEmpty()) {
                            mainApp.showPlayerHistory(historyName);
                        } else {
                            System.out.println("Invalid player name.");
                        }
                        break;

                    case "3":
                        System.out.print("Enter player name: ");
                        String summaryName = scanner.nextLine().trim();
                        if (!summaryName.isEmpty()) {
                            mainApp.showPlayerSummary(summaryName);
                        } else {
                            System.out.println("Invalid player name.");
                        }
                        break;

                    case "4":
                        System.out.print("Enter game ID: ");
                        String gameId = scanner.nextLine().trim();
                        if (!gameId.isEmpty()) {
                            mainApp.analyzeMatch(gameId);
                        } else {
                            System.out.println("Invalid game ID.");
                        }
                        break;

                    case "5":
                        System.out.print("Enter match JSON (or file path, or press Enter for test file): ");
                        String matchInput = scanner.nextLine().trim();
                        if (matchInput.isEmpty()) {
                            // Use test file if Enter is pressed
                            matchInput = "src/test/resources/test_live_match.json";
                            System.out.println("Using test file: " + matchInput);
                        }
                        // Check if input is a file path
                        if (matchInput.endsWith(".json") && !matchInput.contains("{")) {
                            try {
                                // Read from file
                                java.nio.file.Path filePath = java.nio.file.Paths.get(matchInput);
                                if (java.nio.file.Files.exists(filePath)) {
                                    String matchJson = new String(java.nio.file.Files.readAllBytes(filePath));
                                    mainApp.processSingleMatch(matchJson);
                                } else {
                                    System.out.println("File not found: " + matchInput);
                                }
                            } catch (java.io.IOException e) {
                                System.out.println("Error reading file: " + e.getMessage());
                            }
                        } else {
                            // Treat as direct JSON input
                            mainApp.processSingleMatch(matchInput);
                        }
                        break;

                    case "6":
                        // First show the current last games available for removal
                        mainApp.displayGamesForRemoval();

                        System.out.print("Enter index (1=most recent, 2=second most recent, etc.): ");
                        String indexStr = scanner.nextLine().trim();
                        try {
                            int index = Integer.parseInt(indexStr);
                            if (index < 1) {
                                System.out.println("Index must be at least 1.");
                            } else {
                                mainApp.removeRecentMatch(index);
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid index.");
                        }
                        break;

                    case "7":
                        mainApp.viewLast5GamesElo();
                        break;

                    case "8":
                        System.out.println("\nBalance Mega Teams:");
                        System.out.println("  a) From JSON file with player UUIDs");
                        System.out.println("  b) Enter player names manually");
                        System.out.print("Choose option (a/b): ");
                        String balanceChoice = scanner.nextLine().trim().toLowerCase();
                        
                        if (balanceChoice.equals("a")) {
                            System.out.print("Enter player list JSON file path: ");
                            String balanceFile = scanner.nextLine().trim();
                            if (!balanceFile.isEmpty()) {
                                mainApp.balanceTeams(balanceFile);
                            } else {
                                System.out.println("Invalid file path.");
                            }
                        } else if (balanceChoice.equals("b")) {
                            System.out.println("\nEnter player names (one per line, empty line to finish):");
                            java.util.List<String> playerNames = new java.util.ArrayList<>();
                            int playerNum = 1;
                            while (true) {
                                System.out.print("Player " + playerNum + ": ");
                                String name = scanner.nextLine().trim();
                                if (name.isEmpty()) {
                                    break;
                                }
                                playerNames.add(name);
                                playerNum++;
                            }
                            if (playerNames.size() >= 2) {
                                mainApp.balanceTeamsFromNames(playerNames);
                            } else {
                                System.out.println("Need at least 2 players to balance teams.");
                            }
                        } else {
                            System.out.println("Invalid choice.");
                        }
                        break;

                    case "9":
                    case "exit":
                    case "quit":
                        System.out.println("Exiting...");
                        running = false;
                        break;

                    default:
                        System.out.println("Invalid choice. Please try again.");
                        break;
                }

            } catch (java.util.NoSuchElementException e) {
                // No input available, exit gracefully
                System.out.println("\nNo input available. Exiting...");
                running = false;
            }

            if (running && !choice.equals("8") && !choice.equals("exit") && !choice.equals("quit")) {
                System.out.println("\nPress Enter to continue...");
                try {
                    scanner.nextLine();
                } catch (java.util.NoSuchElementException e) {
                    // No input available, continue
                }
            }
        }

        scanner.close();
    }

    /**
     * Prints the interactive menu.
     */
    private void printMenu() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("       BEDWARS ELO CALCULATOR - Main Menu");
        System.out.println("=".repeat(60));
        System.out.println("1. Process All Historical Data");
        System.out.println("   └─ Calculate ELO for all games and generate leaderboards");
        System.out.println();
        System.out.println("2. View Player ELO History");
        System.out.println("   └─ Show detailed match-by-match ELO changes");
        System.out.println();
        System.out.println("3. View Player ELO Summary");
        System.out.println("   └─ Show condensed ELO statistics per mode");
        System.out.println();
        System.out.println("4. Analyze Specific Match");
        System.out.println("   └─ Show detailed ELO calculations for a historical scraped game");
        System.out.println();
        System.out.println("5. Add Single Match");
        System.out.println("   └─ Process a new match and update leaderboards");
        System.out.println("     (press Enter to use test file)");
        System.out.println();
        System.out.println("6. Remove Recent Match");
        System.out.println("   └─ Remove one of the last 5 matches (specify index 1-5)");
        System.out.println();
        System.out.println("7. View Last 5 Games");
        System.out.println("   └─ Show the last 5 games played overall with ELO changes");
        System.out.println();
        System.out.println("8. Balance Mega Teams");
        System.out.println("   ├─ a) From JSON file with player UUIDs");
        System.out.println("   └─ b) Enter player names manually");
        System.out.println();
        System.out.println("9. Exit");
        System.out.println("=".repeat(60));
    }
}


