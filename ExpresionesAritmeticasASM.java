import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public class ExpresionesAritmeticasASM {

    private static int temporalCounter = 1;

    public static void main(String[] args) {
        // Entrada: Solicitar la expresión aritmética al usuario
        String input;
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("Ingrese la expresión aritmética: ");
            input = scanner.nextLine();
        } finally {
            // Scanner no se cierra aquí para posibles usos posteriores
        }

        // Eliminar espacios en blanco de la expresión aritmética
        input = input.replaceAll("\\s+", "");

        // Identificar la variable a la izquierda del '=' y las variables usadas
        String variableIzquierda = identificarVariableIzquierda(input);
        Set<String> variables = identificarVariables(input);

        // Excluir la variable izquierda de la lista de variables a solicitar
        variables.remove(variableIzquierda);

        // Solicitar al usuario los valores de las variables identificadas
        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);

        // Reemplazar las variables en la expresión con sus nombres
        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll(Pattern.quote(entry.getKey()), entry.getKey());
        }

        // Inicializar listas para guardar temporales e instrucciones ASM
        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        // Procesar la expresión aritmética y generar temporales
        procesarExpresion(input, temporales, instruccionesASM);

        // Generar el archivo ASM con las instrucciones y variables declaradas
        generarArchivoASM(instruccionesASM, valoresVariables);

        // Cerrar el Scanner al final
        scanner.close();
    }

    private static String identificarVariableIzquierda(String expresion) {
        // Extraer la variable a la izquierda del operador '='
        int indiceIgual = expresion.indexOf('=');
        if (indiceIgual != -1) {
            return expresion.substring(0, indiceIgual);
        }
        return null;
    }

    private static Set<String> identificarVariables(String expresion) {
        // Identificar todas las variables en la expresión utilizando expresiones
        // regulares
        Set<String> variables = new HashSet<>();
        Pattern variablePattern = Pattern.compile("[a-zA-Z]+");
        Matcher matcher = variablePattern.matcher(expresion);

        while (matcher.find()) {
            variables.add(matcher.group());
        }

        return variables;
    }

    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        // Solicitar al usuario los valores de cada variable identificada
        Map<String, Double> valoresVariables = new HashMap<>();

        for (String variable : variables) {
            System.out.print("Ingrese el valor para la variable '" + variable + "': ");
            double valor = scanner.nextDouble();
            valoresVariables.put(variable, valor);
        }

        return valoresVariables;
    }

    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM) {
        // Resolver expresiones entre paréntesis primero
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcher;

        while ((matcher = parentesisPattern.matcher(expresion)).find()) {
            String subExpresion = matcher.group(1);
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM);
            expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
        }

        // Procesar operadores en orden de precedencia: *, /, +, -, =
        String[] operadores = { "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores = { "MUL", "DIV", "ADD", "SUB", "MOV" };

        for (int i = 0; i < operadores.length; i++) {
            Pattern operacionPattern = Pattern.compile("([a-zA-Z0-9.]+)" + operadores[i] + "([a-zA-Z0-9.]+)");

            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                String operando1 = matcher.group(1);
                String operando2 = matcher.group(2);
                String temporal = "T" + temporalCounter++;

                // Crear la operación para los temporales y las instrucciones ASM
                String operacion = String.format("%s -> %s, %s, %s", temporal, operando1, operando2, nombresOperadores[i]);
                temporales.add(operacion);

                String instruccionASM = String.format("%s %s, %s", nombresOperadores[i], operando1, operando2);
                instruccionesASM.add(instruccionASM);

                // Reemplazar la operación en la expresión por el temporal generado
                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        return expresion;
    }

    private static void generarArchivoASM(List<String> instruccionesASM, Map<String, Double> valoresVariables) {
        // Generar un archivo .ASM con las instrucciones y declaraciones necesarias
        try (FileWriter writer = new FileWriter("Resultado.ASM")) {
            writer.write(".model small\n");
            writer.write(".stack 100h\n");
            writer.write(".data\n");

            // Declarar las variables con sus valores iniciales
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                String variable = entry.getKey();
                double valor = entry.getValue();
                writer.write(String.format("%s DW %s\n", variable, valor));
            }

            // Declarar los temporales como variables
            for (int i = 1; i < temporalCounter; i++) {
                writer.write("T" + i + " DW ?\n");
            }

            writer.write(".code\n");
            writer.write("BEGIN:\n");

            // Escribir las instrucciones de ensamblador
            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            writer.write("    mov ah, 4Ch\n");
            writer.write("    int 21h\n");
            writer.write("END BEGIN\n");

            System.out.println("Archivo ASM generado exitosamente: Resultado.ASM");
        } catch (IOException e) {
            System.err.println("Error al generar el archivo ASM: " + e.getMessage());
        }
    }
}
