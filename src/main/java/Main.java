import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        Set<String> builtins = Set.of("echo", "exit", "type", "pwd");
        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine().trim();

            if (input.equals("exit")) {
                break;
            } else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            }

            else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }

            else if (input.startsWith("type ")) {
                String command = input.substring(5);

                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {
                    File file = new File(dir, command);

                    if (file.exists() && file.canExecute()) {
                        System.out.println(command + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.out.println(command + ": not found");
                }
            } else {

                String[] parts = input.split(" ");
                String command = parts[0];

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {
                    File file = new File(dir, command);

                    if (file.exists() && file.canExecute()) {
                        try {

                            ProcessBuilder pb = new ProcessBuilder(parts);

                            pb.inheritIO();

                            Process process = pb.start();

                            process.waitFor();

                            found = true;

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        break;
                    }
                }

                if (!found) {
                    System.out.println(command + ": not found");
                }
            }
        }

        scanner.close();
    }
}