import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public class ExpresionesAritmeticasASM {

    // Contador global para generar nombres de temporales (T1, T2, etc.)
    private static int temporalCounter = 1;

    public static void main(String[] args) {
        // *****************************************
        // Entrada de la expresión aritmética
        String input;
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("Ingrese la expresión aritmética: ");
            input = scanner.nextLine();
        } finally {
            // Scanner no se cierra aquí, se usará más adelante
        }

        // *****************************************
        // Limpieza de la expresión para eliminar espacios
        input = input.replaceAll("\\s+", "");

        // *****************************************
        // Identificar la variable a la izquierda del '=' y las variables usadas
        String variableIzquierda = identificarVariableIzquierda(input);
        Set<String> variables = identificarVariables(input);

        // Excluir la variable izquierda (no se solicita su valor)
        variables.remove(variableIzquierda);

        // *****************************************
        // Solicitar al usuario los valores de las variables identificadas
        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);

        // *****************************************
        // Reemplazar variables en la expresión con sus nombres
        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll(Pattern.quote(entry.getKey()), entry.getKey());
        }

        // *****************************************
        // Procesar la expresión aritmética y generar temporales
        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        String resultado = procesarExpresion(input, temporales, instruccionesASM);

        // *****************************************
        // Imprimir los temporales generados y el resultado final
        for (String temporal : temporales) {
            System.out.println(temporal);
        }
        System.out.println("Resultado final: " + resultado);

        // *****************************************
        // Generar el archivo ASM con las instrucciones
        generarArchivoASM(instruccionesASM);

        // *****************************************
        // Cerrar el Scanner al final del programa
        scanner.close();
    }

    // *****************************************
    // Identificar la variable a la izquierda del '='
    private static String identificarVariableIzquierda(String expresion) {
        int indiceIgual = expresion.indexOf('=');
        if (indiceIgual != -1) {
            return expresion.substring(0, indiceIgual);
        }
        return null;
    }

    // *****************************************
    // Identificar las variables presentes en la expresión
    private static Set<String> identificarVariables(String expresion) {
        Set<String> variables = new HashSet<>();
        Pattern variablePattern = Pattern.compile("[a-zA-Z]+");
        Matcher matcher = variablePattern.matcher(expresion);

        while (matcher.find()) {
            variables.add(matcher.group());
        }

        return variables;
    }

    // *****************************************
    // Solicitar los valores de las variables identificadas al usuario
    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        Map<String, Double> valoresVariables = new HashMap<>();

        for (String variable : variables) {
            System.out.print("Ingrese el valor para la variable '" + variable + "': ");
            double valor = scanner.nextDouble();
            valoresVariables.put(variable, valor);
        }

        return valoresVariables;
    }

    // *****************************************
    // Procesar la expresión aritmética y generar temporales
    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM) {
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcher;

        // Resolver expresiones dentro de paréntesis primero
        while ((matcher = parentesisPattern.matcher(expresion)).find()) {
            String subExpresion = matcher.group(1);
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM);
            expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
        }

        // Operadores en orden de precedencia
        String[] operadores = { "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores = { "MUL", "DIV", "ADD", "SUB", "MOV" };

        for (int i = 0; i < operadores.length; i++) {
            Pattern operacionPattern = Pattern.compile("([a-zA-Z0-9.]+)" + operadores[i] + "([a-zA-Z0-9.]+)");

            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                String operando1 = matcher.group(1);
                String operando2 = matcher.group(2);
                String temporal = "T" + temporalCounter++;

                String operacion = String.format("%s -> %s, %s, %s", temporal, operando1, operando2, nombresOperadores[i]);
                temporales.add(operacion);

                String instruccionASM = String.format("%s %s, %s", nombresOperadores[i], operando1, operando2);
                instruccionesASM.add(instruccionASM);

                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        return expresion;
    }

    // *****************************************
    // Generar archivo ASM con las instrucciones generadas
    private static void generarArchivoASM(List<String> instruccionesASM) {
        try (FileWriter writer = new FileWriter("resultado.ASM")) {
            writer.write(".model small\n");
            writer.write(".stack 100h\n");
            writer.write(".data\n");

            // Declarar los temporales como variables
            for (int i = 1; i < temporalCounter; i++) {
                writer.write("T" + i + " DW ?\n");
            }

            writer.write(".code\n");
            writer.write("start:\n");

            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            writer.write("    mov ah, 4Ch\n");
            writer.write("    int 21h\n");
            writer.write("end start\n");

            System.out.println("Archivo ASM generado exitosamente: Resultado.ASM");
        } catch (IOException e) {
            System.err.println("Error al generar el archivo ASM: " + e.getMessage());
        }
    }
}
