import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public class ExpresionesAritmeticasASM {

    // Contador global para generar nombres de temporales (T1, T2, etc.)
    private static int temporalCounter = 1;

    public static void main(String[] args) {
        String input;
        Scanner scanner = new Scanner(System.in); // Crear el Scanner fuera del bloque try

        try {
            System.out.println("Ingrese la expresión aritmética: ");
            input = scanner.nextLine();
        } finally {
            // No cerramos el scanner aquí para seguir usándolo más adelante
        }

        // Limpiar la expresión de espacios innecesarios
        input = input.replaceAll("\\s+", "");

        // Obtener las variables de la expresión y sus valores
        Map<String, Double> valoresVariables = obtenerValoresDeVariables(input, scanner);

        // Reemplazar las variables en la expresión con sus valores
        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll(Pattern.quote(entry.getKey()), entry.getValue().toString());
        }

        // Lista para almacenar los temporales generados
        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        // Procesar la expresión ingresada
        String resultado = procesarExpresion(input, temporales, instruccionesASM);

        // Imprimir todos los temporales generados
        for (String temporal : temporales) {
            System.out.println(temporal);
        }
        System.out.println("Resultado final: " + resultado);

        // Generar el archivo .ASM
        generarArchivoASM(instruccionesASM);

        // Cerrar el scanner al final del programa
        scanner.close();
    }

    /**
     * Identifica las variables en una expresión y solicita al usuario que ingrese sus valores.
     *
     * @param expresion La expresión aritmética que puede contener variables.
     * @param scanner   El Scanner para leer los valores ingresados por el usuario.
     * @return Un mapa con los nombres de las variables y sus valores.
     */
    private static Map<String, Double> obtenerValoresDeVariables(String expresion, Scanner scanner) {
        Map<String, Double> valoresVariables = new HashMap<>();

        // Buscar la parte de la expresión después del signo =
        int indiceIgual = expresion.indexOf('=');
        if (indiceIgual != -1) {
            String parteDerecha = expresion.substring(indiceIgual + 1);

            // Buscar todas las variables en la parte derecha de la expresión
            Pattern variablePattern = Pattern.compile("[a-zA-Z]+");
            Matcher matcher = variablePattern.matcher(parteDerecha);

            while (matcher.find()) {
                String variable = matcher.group();
                if (!valoresVariables.containsKey(variable)) {
                    System.out.print("Ingrese el valor para la variable '" + variable + "': ");
                    double valor = scanner.nextDouble();
                    valoresVariables.put(variable, valor);
                }
            }
        }

        return valoresVariables;
    }

    /**
     * Procesa una expresión aritmética para resolverla y generar temporales.
     *
     * @param expresion        La expresión aritmética a resolver.
     * @param temporales       Lista para almacenar los temporales generados.
     * @param instruccionesASM Lista para almacenar las instrucciones en ensamblador.
     * @return El nombre del último temporal generado que representa el resultado de la expresión.
     */
    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM) {
        // Resolver primero las expresiones contenidas dentro de paréntesis
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcher;

        // Buscar y resolver todas las subexpresiones dentro de paréntesis
        while ((matcher = parentesisPattern.matcher(expresion)).find()) {
            // Extraer el contenido dentro de los paréntesis
            String subExpresion = matcher.group(1);

            // Resolver la subexpresión recursivamente
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM);

            // Reemplazar el paréntesis completo con el temporal generado
            expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
        }

        // Definir los operadores en orden de precedencia
        String[] operadores = { "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores = { "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Procesar cada operador en el orden de precedencia
        for (int i = 0; i < operadores.length; i++) {
            Pattern operacionPattern = Pattern.compile("([a-zA-Z0-9.]+)" + operadores[i] + "([a-zA-Z0-9.]+)");

            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                String operando1 = matcher.group(1);
                String operando2 = matcher.group(2);
                String temporal = "T" + temporalCounter++;

                String operacion = String.format("%s -> %s, %s, %s", temporal, operando1, operando2,
                        nombresOperadores[i]);

                temporales.add(operacion);

                String instruccionASM = String.format("%s %s, %s", nombresOperadores[i], operando1, operando2);
                instruccionesASM.add(instruccionASM);

                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        return expresion;
    }

    /**
     * Genera un archivo .ASM listo para compilar en emu8086.
     *
     * @param instruccionesASM Lista de instrucciones en ensamblador.
     */
    private static void generarArchivoASM(List<String> instruccionesASM) {
        try (FileWriter writer = new FileWriter("resultado.asm")) {
            writer.write(".model small\n");
            writer.write(".stack 100h\n");
            writer.write(".data\n");

            // Declarar las variables y temporales
            for (int i = 1; i < temporalCounter; i++) {
                writer.write("T" + i + " DW ?\n"); // Temporales como palabras de 16 bits
            }

            writer.write(".code\n");
            writer.write("start:\n");

            for (String instruccion : instruccionesASM) {
                // Convertir las instrucciones generadas al formato emu8086
                if (instruccion.startsWith("MUL") || instruccion.startsWith("DIV")) {
                    // MUL y DIV en emu8086 usan AX por defecto
                    String[] partes = instruccion.split(" ");
                    writer.write("    MOV AX, " + partes[1] + "\n");
                    writer.write("    " + partes[0] + " " + partes[2] + "\n");
                } else if (instruccion.startsWith("ADD") || instruccion.startsWith("SUB")) {
                    // ADD y SUB
                    String[] partes = instruccion.split(" ");
                    writer.write("    MOV AX, " + partes[1] + "\n");
                    writer.write("    " + partes[0] + " AX, " + partes[2] + "\n");
                } else if (instruccion.startsWith("MOV")) {
                    // MOV
                    String[] partes = instruccion.split(" ");
                    writer.write("    MOV " + partes[1] + ", " + partes[2] + "\n");
                }
            }

            writer.write("    mov ah, 4Ch\n");
            writer.write("    int 21h\n");
            writer.write("end start\n");

            System.out.println("Archivo ASM generado exitosamente: resultado.asm");
        } catch (IOException e) {
            System.err.println("Error al generar el archivo ASM: " + e.getMessage());
        }
    }
}
