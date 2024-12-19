import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public class ExpresionesAritmeticasASM {

    private static int temporalCounter = 1;

    @SuppressWarnings("ConvertToTryWithResources")
    public static void main(String[] args) {
        String input;
        Scanner scanner = new Scanner(System.in);

        try {
            while (true) {
                System.out.println("Ingrese la expresión aritmética: ");
                input = scanner.nextLine();

                // Eliminar espacios en blanco
                input = input.replaceAll("\\s+", "");

                // Validar expresión
                if (esExpresionValida(input)) {
                    break; // Salir del ciclo si la expresión es válida
                } else {
                    System.out.println("Expresión inválida. Intente de nuevo.");
                }
            }
        } finally {
            // Scanner no se cierra aquí porque se usará más adelante
        }

        String variableIzquierda = identificarVariableIzquierda(input);
        Set<String> variables = identificarVariables(input);

        variables.remove(variableIzquierda);

        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);

        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll(Pattern.quote(entry.getKey()), entry.getKey());
        }

        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        String Resultado = procesarExpresion(input, temporales, instruccionesASM);

        for (String temporal : temporales) {
            System.out.println(temporal);
        }
        System.out.println("Resultado final: " + Resultado);

        generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda);

        scanner.close();
    }

    private static boolean esExpresionValida(String expresion) {
        // Validar que no haya operadores consecutivos
        Pattern operadoresConsecutivos = Pattern.compile("[+\\-*/]{2,}");
        Matcher matcherOperadores = operadoresConsecutivos.matcher(expresion);
        if (matcherOperadores.find()) {
            return false;
        }
    
        // Validar que haya un operador antes de cualquier paréntesis
        Pattern parenSinOperador = Pattern.compile("(?<![+\\-*/(])\\(");
        Matcher matcherParenSinOperador = parenSinOperador.matcher(expresion);
        if (matcherParenSinOperador.find()) {
            return false;
        }
    
        // Validar que no haya números seguidos de paréntesis sin operador
        Pattern numeroSinOperador = Pattern.compile("\\d+\\(");
        Matcher matcherNumeroSinOperador = numeroSinOperador.matcher(expresion);
        if (matcherNumeroSinOperador.find()) {
            return false;
        }
    
        return true;
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

                String instruccionASM = generarInstruccionASM(nombresOperadores[i], operando1, operando2, temporal);
                instruccionesASM.add(instruccionASM);

                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        return expresion;
    }

    private static String generarInstruccionASM(String operador, String operando1, String operando2, String temporal) {
        StringBuilder instruccion = new StringBuilder();

        switch (operador) {
            case "MUL" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    MUL %s", operando2)).append("\n"); // Resultado en AX
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar en temporal
            }

            case "DIV" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    XOR DX, DX")).append("\n"); // Limpiar DX para la división
                instruccion.append(String.format("    DIV %s", operando2)).append("\n"); // Cociente en AX
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar en temporal
            }

            case "ADD" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    ADD AX, %s", operando2)).append("\n"); // Suma en AX
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar en temporal
            }

            case "SUB" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    SUB AX, %s", operando2)).append("\n"); // Resta en AX
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar en temporal
            }

            case "MOV" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando2)).append("\n"); // Cargar valor en AX
                instruccion.append(String.format("    MOV %s, AX", operando1)); // Mover a destino
            }

            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        }

        return instruccion.toString();
    }

    private static void generarArchivoASM(List<String> instruccionesASM, Map<String, Double> valoresVariables,
            String variableIzquierda) {
        try (FileWriter writer = new FileWriter("Resultado.ASM")) {
            writer.write(".MODEL SMALL\n");
            writer.write(".STACK 100h\n\n");
            writer.write(".DATA\n");

            // Declarar variables con sus valores correspondientes
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                writer.write("    " + entry.getKey() + " DW " + convertirValorASM(entry.getValue()) + "\n");
            }

            if (variableIzquierda != null) {
                writer.write("    " + variableIzquierda + " DW ?\n");
            }

            for (int i = 1; i < temporalCounter; i++) {
                writer.write("    T" + i + " DW ?\n");
            }

            writer.write("    Resultado DB '" + variableIzquierda + " =   $'\n");
            writer.write("    value DB 5 DUP('$') ; Buffer para el valor de X en texto\n\n");

            writer.write(".CODE\n");
            writer.write("start:\n");

            writer.write("    MOV AX, @DATA\n");
            writer.write("    MOV DS, AX\n");

            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            writer.write("\n    ; Convertir " + variableIzquierda + " a texto\n");
            writer.write("    MOV AX, " + variableIzquierda + "\n");
            writer.write("    MOV CX, 5\n");
            writer.write("    LEA DI, value\n");
            writer.write("    MOV BX, 10\n\n");
            writer.write("next_digit:\n");
            writer.write("    XOR DX, DX\n");
            writer.write("    DIV BX\n");
            writer.write("    ADD DL, '0'\n");
            writer.write("    DEC DI\n");
            writer.write("    MOV [DI], DL\n");
            writer.write("    DEC CX\n");
            writer.write("    TEST AX, AX\n");
            writer.write("    JNZ next_digit\n\n");

            writer.write("    ; Mostrar resultado\n");
            writer.write("    LEA DX, Resultado\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n");

            writer.write("    LEA DX, value\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            writer.write("    ; Terminar programa\n");
            writer.write("    MOV AH, 4Ch\n");
            writer.write("    INT 21h\n");

            writer.write("END start\n");

            System.out.println("Archivo ASM generado exitosamente: Resultado.ASM");
        } catch (IOException e) {
            System.err.println("Error al generar el archivo ASM: " + e.getMessage());
        }
    }

    private static String convertirValorASM(double valor) {
        return String.format("%.0f", valor);
    }
}