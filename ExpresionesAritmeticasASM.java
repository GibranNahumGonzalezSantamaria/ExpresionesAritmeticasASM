import java.io.FileWriter; // Importación para escribir en un archivo
import java.io.IOException; // Importación para manejar excepciones de entrada/salida
import java.util.*; // Importación para trabajar con colecciones y utilidades
import java.util.regex.*; // Importación para trabajar con expresiones regulares

public class ExpresionesAritmeticasASM {

    // Contador para variables temporales generadas
    private static int temporalCounter = 1;

    @SuppressWarnings("ConvertToTryWithResources") // Supresión de advertencia específica
    public static void main(String[] args) {
        String input; // Almacena la expresión ingresada por el usuario
        Scanner scanner = new Scanner(System.in); // Para leer la entrada del usuario

        try {
            while (true) {
                System.out.println("Ingrese la expresión aritmética: ");
                input = scanner.nextLine();

                // Eliminar espacios en blanco de la entrada
                input = input.replaceAll("\\s+", "");

                // Validar si la expresión es válida
                if (esExpresionValida(input)) {
                    break; // Salir del ciclo si la expresión es válida
                } else {
                    System.out.println("Expresión inválida. Intente de nuevo.");
                }
            }
        } finally {
            // El scanner no se cierra aún porque será utilizado posteriormente
        }

        // Identificar la variable en el lado izquierdo del '='
        String variableIzquierda = identificarVariableIzquierda(input);

        // Identificar todas las variables en la expresión
        Set<String> variables = identificarVariables(input);

        // Eliminar la variable izquierda de la lista de variables
        variables.remove(variableIzquierda);

        // Obtener valores de las variables desde la entrada del usuario
        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);

        // Reemplazar las variables en la expresión con sus nombres
        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll(Pattern.quote(entry.getKey()), entry.getKey());
        }

        // Listas para almacenar temporales e instrucciones ASM
        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        // Procesar la expresión y generar el resultado final
        String Resultado = procesarExpresion(input, temporales, instruccionesASM);

        // Mostrar temporales generados
        for (String temporal : temporales) {
            System.out.println(temporal);
        }
        System.out.println("Resultado final: " + Resultado);

        // Generar el archivo ASM basado en las instrucciones
        generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda);

        scanner.close(); // Cerrar el scanner al final
    }

    // Valida la estructura general de la expresión ingresada
    private static boolean esExpresionValida(String expresion) {
        // Verificar que no haya operadores consecutivos
        Pattern operadoresConsecutivos = Pattern.compile("[+\\-*/=]{2,}");
        Matcher matcherOperadores = operadoresConsecutivos.matcher(expresion);
        if (matcherOperadores.find()) {
            return false;
        }

        // Validar que no haya paréntesis sin operador antes
        Pattern parenSinOperador = Pattern.compile("(?<![+\\-*/(])\\(");
        Matcher matcherParenSinOperador = parenSinOperador.matcher(expresion);
        if (matcherParenSinOperador.find()) {
            return false;
        }

        // Verificar que no haya números seguidos de paréntesis sin operador
        Pattern numeroSinOperador = Pattern.compile("\\d+\\(");
        Matcher matcherNumeroSinOperador = numeroSinOperador.matcher(expresion);
        if (matcherNumeroSinOperador.find()) {
            return false;
        }

        // Verificar que la expresión contenga un signo '='
        if (!expresion.contains("=")) {
            return false;
        }

        // Verificar que no haya números en el lado izquierdo de '='
        Pattern izquierdaIncorrecta = Pattern.compile("(^|[^=])\\d+(\\.?\\d+)?");
        Matcher matcherIzquierdaIncorrecta = izquierdaIncorrecta.matcher(expresion.split("=")[0]);
        if (matcherIzquierdaIncorrecta.find()) {
            return false;
        }

        return true; // La expresión es válida
    }

    // Identifica la variable en el lado izquierdo del '='
    private static String identificarVariableIzquierda(String expresion) {
        int indiceIgual = expresion.indexOf('=');
        if (indiceIgual != -1) {
            return expresion.substring(0, indiceIgual);
        }
        return null; // Devuelve null si no se encuentra '='
    }

    // Identifica todas las variables en la expresión usando expresiones regulares
    private static Set<String> identificarVariables(String expresion) {
        Set<String> variables = new HashSet<>();
        Pattern variablePattern = Pattern.compile("[a-zA-Z]+");
        Matcher matcher = variablePattern.matcher(expresion);

        while (matcher.find()) {
            variables.add(matcher.group()); // Agregar cada variable encontrada
        }

        return variables;
    }

    // Solicita valores de las variables al usuario
    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        Map<String, Double> valoresVariables = new HashMap<>();

        for (String variable : variables) {
            System.out.print("Ingrese el valor para la variable '" + variable + "': ");
            double valor = scanner.nextDouble(); // Leer el valor ingresado
            valoresVariables.put(variable, valor); // Guardar en el mapa
        }

        return valoresVariables;
    }

    // Procesa la expresión aritmética y genera temporales e instrucciones ASM
    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM) {
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcher;

        // Procesar paréntesis primero
        while ((matcher = parentesisPattern.matcher(expresion)).find()) {
            String subExpresion = matcher.group(1); // Subexpresión dentro de paréntesis
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM);
            expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal); // Reemplazar con temporal
        }

        // Definir operadores y sus nombres equivalentes en ASM
        String[] operadores = { "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores = { "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Procesar las operaciones aritméticas según la prioridad
        for (int i = 0; i < operadores.length; i++) {
            Pattern operacionPattern = Pattern.compile("([a-zA-Z0-9.]+)" + operadores[i] + "([a-zA-Z0-9.]+)");

            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                String operando1 = matcher.group(1); // Operando 1
                String operando2 = matcher.group(2); // Operando 2
                String temporal = "T" + temporalCounter++; // Generar temporal

                // Crear una operación para la lista de temporales
                String operacion = String.format("%s -> %s, %s, %s", temporal, operando1, operando2, nombresOperadores[i]);
                temporales.add(operacion);

                // Generar instrucción ASM para la operación
                String instruccionASM = generarInstruccionASM(nombresOperadores[i], operando1, operando2, temporal);
                instruccionesASM.add(instruccionASM);

                // Reemplazar la operación con el temporal
                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        return expresion; // Devolver la expresión resultante
    }

    // Genera una instrucción ASM específica según el operador
    private static String generarInstruccionASM(String operador, String operando1, String operando2, String temporal) {
        StringBuilder instruccion = new StringBuilder();

        switch (operador) {
            case "MUL" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    MOV BX, %s", operando2)).append("\n");
                instruccion.append("    MUL BX\n"); // Multiplicación
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar resultado
            }

            case "DIV" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append("    XOR DX, DX\n"); // Limpieza para división
                instruccion.append(String.format("    MOV BX, %s", operando2)).append("\n");
                instruccion.append("    DIV BX\n"); // División
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar resultado
            }

            case "ADD" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    ADD AX, %s", operando2)).append("\n"); // Suma
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar resultado
            }

            case "SUB" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    SUB AX, %s", operando2)).append("\n"); // Resta
                instruccion.append(String.format("    MOV %s, AX", temporal)); // Guardar resultado
            }

            case "MOV" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando2)).append("\n"); // Cargar en AX
                instruccion.append(String.format("    MOV %s, AX", operando1)); // Mover a destino
            }

            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        }

        return instruccion.toString();
    }

    // Genera el archivo ASM final
    private static void generarArchivoASM(List<String> instruccionesASM, Map<String, Double> valoresVariables,
                                          String variableIzquierda) {
        try (FileWriter writer = new FileWriter("Resultado.ASM")) {
            writer.write(".MODEL SMALL\n");
            writer.write(".STACK 100h\n\n");
            writer.write(".DATA\n");

            // Declarar variables en el segmento .DATA
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
            writer.write("    value DB 5 DUP('$') ;Buffer para el valor en texto\n\n");

            writer.write(".CODE\n");
            writer.write("start:\n");

            // Inicialización de segmento de datos
            writer.write("    MOV AX, @DATA\n");
            writer.write("    MOV DS, AX\n");

            // Escribir instrucciones ASM generadas
            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            // Rutina para convertir variable en texto y mostrar resultado
            writer.write("\n    ;Convertir " + variableIzquierda + " a texto\n");
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

            // Mostrar el resultado
            writer.write("    LEA DX, Resultado\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n");

            writer.write("    LEA DX, value\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Terminar el programa
            writer.write("    MOV AH, 4Ch\n");
            writer.write("    INT 21h\n");

            writer.write("END start\n");

            System.out.println("Archivo ASM generado exitosamente: Resultado.ASM");
        } catch (IOException e) {
            System.err.println("Error al generar el archivo ASM: " + e.getMessage());
        }
    }

    // Convierte un valor double a un formato compatible con ASM
    private static String convertirValorASM(double valor) {
        return String.format("%.0f", valor);
    }
}