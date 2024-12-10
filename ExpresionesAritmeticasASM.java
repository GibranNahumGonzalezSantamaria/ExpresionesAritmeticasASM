import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public class ExpresionesAritmeticasASM {

    private static int temporalCounter = 1;

    public static void main(String[] args) {
        String input;
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("Ingrese la expresión aritmética: ");
            input = scanner.nextLine();
        } finally {
            // Scanner no se cierra aquí porque se usará más adelante
        }

        input = input.replaceAll("\\s+", "");

        String variableIzquierda = identificarVariableIzquierda(input);
        Set<String> variables = identificarVariables(input);

        variables.remove(variableIzquierda);

        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);

        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll(Pattern.quote(entry.getKey()), entry.getKey());
        }

        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        String resultado = procesarExpresion(input, temporales, instruccionesASM);

        for (String temporal : temporales) {
            System.out.println(temporal);
        }
        System.out.println("Resultado final: " + resultado);

        generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda);

        scanner.close();
    }

    private static String identificarVariableIzquierda(String expresion) {
        int indiceIgual = expresion.indexOf('=');
        if (indiceIgual != -1) {
            return expresion.substring(0, indiceIgual);
        }
        return null;
    }

    private static Set<String> identificarVariables(String expresion) {
        Set<String> variables = new HashSet<>();
        Pattern variablePattern = Pattern.compile("[a-zA-Z]+");
        Matcher matcher = variablePattern.matcher(expresion);

        while (matcher.find()) {
            variables.add(matcher.group());
        }

        return variables;
    }

    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        Map<String, Double> valoresVariables = new HashMap<>();

        for (String variable : variables) {
            System.out.print("Ingrese el valor para la variable '" + variable + "': ");
            double valor = scanner.nextDouble();
            valoresVariables.put(variable, valor);
        }

        return valoresVariables;
    }

    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM) {
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcher;

        while ((matcher = parentesisPattern.matcher(expresion)).find()) {
            String subExpresion = matcher.group(1);
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM);
            expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
        }

        String[] operadores = {"\\*", "/", "\\+", "-", "="};
        String[] nombresOperadores = {"MUL", "DIV", "ADD", "SUB", "MOV"};

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

    private static void generarArchivoASM(List<String> instruccionesASM, Map<String, Double> valoresVariables, String variableIzquierda) {
        try (FileWriter writer = new FileWriter("resultado.ASM")) {
            writer.write(".model small\n");
            writer.write(".stack 100h\n");
            writer.write(".data\n");

            // Declarar variables con sus valores correspondientes
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                writer.write(entry.getKey() + " DW " + convertirValorASM(entry.getValue()) + "\n");
            }

            if (variableIzquierda != null) {
                writer.write(variableIzquierda + " DW ?\n");
            }

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

    private static String convertirValorASM(double valor) {
        return String.format("%.2f", valor).replace(".", "");
    }
}
