import java.io.File;
import java.io.FileWriter; // Importación para escribir en un archivo
import java.io.IOException; // Importación para manejar excepciones de entrada/salida
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*; // Importación para trabajar con colecciones y utilidades
import java.util.regex.*; // Importación para trabajar con expresiones regulares
import javax.swing.JFileChooser;

public class ExpresionesAritmeticasASM {

    // Contador para variables temporales generadas
    private static int temporalCounter = 1;

    @SuppressWarnings("ConvertToTryWithResources") // Supresión de advertencia específica
    public static void main(String[] args) {
        String input; // Almacena la expresión ingresada por el usuario

        // Bloque para seleccionar el archivo y procesar su contenido
        String expresionAritmetica = "";
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Seleccione un archivo .txt");

        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile.getName().endsWith(".txt")) {
                try {
                    String contenido = new String(Files.readAllBytes(Paths.get(selectedFile.getAbsolutePath())));
                    expresionAritmetica = contenido.replaceAll("\\s+", "");
                } catch (IOException e) {
                    System.err.println("Error al leer el archivo: " + e.getMessage());
                    return; // Salir del programa si hay un error al leer el archivo
                }
            } else {
                System.err.println("Por favor, seleccione un archivo con extensión .txt");
                return; // Salir del programa si el archivo no es .txt
            }
        } else {
            System.out.println("No se seleccionó ningún archivo.");
            return; // Salir del programa si no se seleccionó un archivo
        }

        // Asignar el contenido del archivo a la variable input
        input = expresionAritmetica;
        System.out.println("\nExpresión Aritmética: " + expresionAritmetica + "\n");

        // Validar si la expresión es válida
        if (!esExpresionValida(input)) {
            System.out.println("Expresión inválida en el archivo. Verifique el contenido.");
            return; // Salir si la expresión no es válida
        }

        // Identificar la variable en el lado izquierdo del '='
        String variableIzquierda = identificarVariableIzquierda(input);

        // Identificar todas las variables en la expresión
        Set<String> variables = identificarVariables(input);

        // Eliminar la variable izquierda de la lista de variables
        variables.remove(variableIzquierda);

        // Obtener valores de las variables desde la entrada del usuario
        Scanner scanner = new Scanner(System.in); // Scanner para entrada de valores de variables
        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);

        // Reemplazar las variables en la expresión con sus valores
        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getKey());
        }

        // Listas para almacenar temporales e instrucciones ASM
        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        // Procesar la expresión y generar el resultado final
        procesarExpresion(input, temporales, instruccionesASM, valoresVariables);

        System.out.print("\n");
        // Mostrar temporales generados
        for (String temporal : temporales) {
            System.out.println(temporal);
        }

        // Obtener el resultado numérico final desde los valores de las variables
        double resultadoNumerico = valoresVariables.get(variableIzquierda);
        System.out.println("\n - Resultado numérico calculado: " + String.format("%.3f", resultadoNumerico) + "\n");

        // Generar el archivo ASM basado en las instrucciones
        generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda);

        scanner.close(); // Cerrar el scanner al final
    }

    // Valida la estructura general de la expresión ingresada
    private static boolean esExpresionValida(String expresion) {
        // Verificar que no haya operadores consecutivos (e.g., ++, **, //, etc.)
        Pattern operadoresConsecutivos = Pattern.compile("[+\\-*/=]{2,}");
        Matcher matcherOperadores = operadoresConsecutivos.matcher(expresion);
        if (matcherOperadores.find()) {
            return false; // Expresión inválida si encuentra operadores consecutivos
        }

        // Validar que no haya paréntesis sin operador antes (e.g., "a(b)")
        Pattern parenSinOperador = Pattern.compile("(?<![+\\-*/(=])\\(");
        Matcher matcherParenSinOperador = parenSinOperador.matcher(expresion);
        if (matcherParenSinOperador.find()) {
            return false; // Expresión inválida si hay un paréntesis sin operador antes
        }

        // Verificar que no haya números seguidos de paréntesis sin operador (e.g., "5(3+2)")
        Pattern numeroSinOperador = Pattern.compile("\\d+\\(");
        Matcher matcherNumeroSinOperador = numeroSinOperador.matcher(expresion);
        if (matcherNumeroSinOperador.find()) {
            return false; // Expresión inválida si un número es seguido directamente por '('
        }

        // Verificar que la expresión contenga exactamente un signo '='
        long countIgual = expresion.chars().filter(ch -> ch == '=').count();
        if (countIgual != 1) {
            return false; // Expresión inválida si no hay o hay más de un '='
        }

        // Verificar que el lado izquierdo del '=' sea una variable válida
        String[] partes = expresion.split("=", 2); // Divide la expresión en izquierda y derecha
        String ladoIzquierdo = partes[0];
        if (!ladoIzquierdo.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { // Permitir letras, números y guiones bajos
            return false; // El lado izquierdo debe ser un identificador válido
        }

        // Verificar que no haya caracteres inválidos en la expresión
        Pattern caracteresInvalidos = Pattern.compile("[^a-zA-Z0-9_+\\-*/=().]");
        Matcher matcherCaracteresInvalidos = caracteresInvalidos.matcher(expresion);
        if (matcherCaracteresInvalidos.find()) {
            return false; // Expresión inválida si contiene caracteres no permitidos
        }

        // Verificar que los paréntesis estén balanceados
        int contadorParentesis = 0;
        for (char c : expresion.toCharArray()) {
            if (c == '(')
                contadorParentesis++;
            if (c == ')')
                contadorParentesis--;
            if (contadorParentesis < 0)
                return false; // Más ')' que '('
        }
        if (contadorParentesis != 0) {
            return false; // Paréntesis desbalanceados
        }

        return true; // La expresión es válida si pasa todas las verificaciones
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
        Pattern variablePattern = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*"); // Ajuste para incluir '_' en las variables
        Matcher matcher = variablePattern.matcher(expresion);

        while (matcher.find()) {
            variables.add(matcher.group()); // Agregar cada variable encontrada
        }

        return variables;
    }

    // Solicita valores de las variables al usuario
    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        Map<String, Double> valoresVariables = new HashMap<>();
        Pattern patternVariable = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*"); // Patrón para detectar nombres de variables

        for (String variable : variables) {
            while (true) {
                System.out.print(" * Ingrese el valor para la variable '" + variable + "': ");
                String entrada = scanner.next();

                // Validar si la entrada es un número válido
                try {
                    double valor = Double.parseDouble(entrada);
                    valoresVariables.put(variable, valor); // Guardar el valor si es válido
                    break; // Salir del ciclo si se ingresó un número válido
                } catch (NumberFormatException e) {
                    // Si no es un número, verificar si es un nombre de variable
                    if (patternVariable.matcher(entrada).matches()) {
                        System.out.println("Error: No se permite usar el nombre de otra variable como valor.");
                    } else {
                        System.out.println("Error: Entrada no válida. Solo se permiten números.");
                    }
                }
            }
        }

        return valoresVariables;
    }

    // Procesa la expresión aritmética y genera temporales e instrucciones ASM
    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM, Map<String, Double> valoresVariables) {
        String[] operadoresJerarquia = { "\\(", "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores = { "PAREN", "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Procesar los paréntesis primero
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)"); // Encuentra contenido dentro de paréntesis
        Matcher matcherParentesis;
        while ((matcherParentesis = parentesisPattern.matcher(expresion)).find()) {
            String subExpresion = matcherParentesis.group(1); // Obtener la subexpresión dentro de los paréntesis
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM, valoresVariables); // Procesar recursivamente
            expresion = expresion.replaceFirst(Pattern.quote("(" + subExpresion + ")"), temporal); // Reemplazar en la expresión original
        }

        // Procesar los operadores en orden jerárquico
        for (int i = 1; i < operadoresJerarquia.length; i++) {
            Pattern operacionPattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)" + operadoresJerarquia[i] + "([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)");
            Matcher matcher;
            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                String operando1 = matcher.group(1); // Operando 1
                String operando2 = matcher.group(3); // Operando 2
                String temporal = "T" + temporalCounter++; // Generar temporal

                // Manejo específico para la operación de asignación (`=`):
                if (nombresOperadores[i].equals("MOV")) {
                    // En el caso de `=`, simplemente asignamos el valor de `operando2` a `operando1`
                    double valor2 = valoresVariables.containsKey(operando2) ? valoresVariables.get(operando2) : Double.parseDouble(operando2);
                    valoresVariables.put(operando1, valor2);

                    // Generar instrucción ASM para la operación MOV
                    String instruccionASM = generarInstruccionASM(nombresOperadores[i], operando1, operando2, temporal);
                    instruccionesASM.add(instruccionASM);

                    return operando1; // Devolver la variable izquierda
                }

                // Calcular el resultado de la operación actual
                double valor1 = valoresVariables.containsKey(operando1) ? valoresVariables.get(operando1) : Double.parseDouble(operando1);
                double valor2 = valoresVariables.containsKey(operando2) ? valoresVariables.get(operando2) : Double.parseDouble(operando2);
                double resultado = calcularResultado(valor1, valor2, nombresOperadores[i]); // Realiza el cálculo

                // Actualizar valores de variables temporales
                valoresVariables.put(temporal, resultado);

                // Crear una operación para la lista de temporales
                String operacion = String.format("    %s -> %s, %s, %s", temporal, operando1, operando2, nombresOperadores[i]);
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

    // Método auxiliar para calcular el resultado de las operaciones en tiempo real
    private static double calcularResultado(double operando1, double operando2, String operador) {
        return switch (operador) {
            case "MUL" -> operando1 * operando2;
            case "DIV" -> operando1 / operando2;
            case "ADD" -> operando1 + operando2;
            case "SUB" -> operando1 - operando2;
            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        };
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
                writer.write("    " + entry.getKey() + " DW " + convertirValorASM(entry.getKey(), entry.getValue()) + "\n");
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

            System.out.println(" - Archivo ASM generado exitosamente: Resultado.ASM\n");
        } catch (IOException e) {
            System.err.println(" - Error al generar el archivo ASM: " + e.getMessage());
        }
    }

    // Convierte un valor double a un formato compatible con ASM, separando parte entera y decimal
    private static String convertirValorASM(String variable, double valor) {
        // Obtiene la parte entera y la parte decimal, limitados a tres dígitos
        int parteEntera = (int) valor; // Parte entera
        int parteDecimal = (int) Math.round((valor - parteEntera) * 1000); // Tres decimales como entero

        // Ajusta las partes al formato de tres dígitos
        String parteEnteraFormateada = String.format("%03d", parteEntera);
        String parteDecimalFormateada = String.format("%03d", parteDecimal);

        // Retorna el formato ASM deseado
        return String.format("%s\n    %s_D DW %s ;Decimales de " + variable, parteEnteraFormateada, variable,
                parteDecimalFormateada);
    }
}
