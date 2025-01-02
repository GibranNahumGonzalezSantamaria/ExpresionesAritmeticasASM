// Importar clase para representar archivos
import java.io.File;

// Importar clase para escribir en un archivo
import java.io.FileWriter;

// Importar clase para manejar excepciones de entrada/salida
import java.io.IOException;

// Importar clases para leer bytes de archivos y manejar rutas de archivos
import java.nio.file.Files;
import java.nio.file.Paths;

// Importar colecciones y utilidades
import java.util.*;

// Importar clases para trabajar con expresiones regulares
import java.util.regex.*;

// Importar clase para selector de archivos
import javax.swing.JFileChooser;

// Declarar clase principal
public class ExpresionesAritmeticasASM {
    // Declarar contador para variables temporales
    private static int temporalCounter = 1;

    // Suprimir advertencia de no usar try-with-resources en Scanner
    @SuppressWarnings("ConvertToTryWithResources")
    // Declarar método principal
    public static void main(String[] args) {
        // Declarar variable para almacenar expresión leída
        String input;

        // Declarar variable para almacenar expresión aritmética sin espacios
        String expresionAritmetica = "";

        // Crear selector de archivos
        JFileChooser fileChooser = new JFileChooser();
        // Configurar título de ventana de selección
        fileChooser.setDialogTitle("Seleccione un archivo .txt");

        // Mostrar diálogo y almacenar resultado de la selección
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            // Obtener archivo seleccionado
            File selectedFile = fileChooser.getSelectedFile();
            // Verificar extensión .txt
            if (selectedFile.getName().endsWith(".txt")) {
                try {
                    // Leer contenido del archivo y almacenar en 'expresionAritmetica'
                    String contenido = new String(Files.readAllBytes(Paths.get(selectedFile.getAbsolutePath())));
                    // Eliminar espacios en blanco con expresiones regulares
                    expresionAritmetica = contenido.replaceAll("\\s+", "");
                } catch (IOException e) {
                    // Imprimir error si ocurre problema al leer
                    System.err.println("Error al leer el archivo: " + e.getMessage());
                    return; // Salir si no se pudo leer el archivo
                }
            } else {
                // Mostrar error si el archivo no es .txt
                System.err.println("Por favor, seleccione un archivo con extensión .txt");
                return; // Terminar ejecución si no es .txt
            }
        } else {
            // Mensaje si no se selecciona ningún archivo
            System.out.println("No se seleccionó ningún archivo.");
            return; // Salir del programa
        }

        // Asignar la expresión procesada a la variable 'input'
        input = expresionAritmetica;
        // Mostrar la expresión aritmética en consola
        System.out.println("\nExpresión Aritmética: " + expresionAritmetica + "\n");

        // Validar si la expresión es válida
        if (!esExpresionValida(input)) {
            // Mensaje de error si la expresión no es válida
            System.out.println("Expresión inválida en el archivo. Verifique el contenido.");
            return; // Salir si la expresión no cumple validaciones
        }

        // Identificar la variable en el lado izquierdo del '='
        String variableIzquierda = identificarVariableIzquierda(input);

        // Identificar todas las variables dentro de la expresión
        Set<String> variables = identificarVariables(input);

        // Remover la variable izquierda del conjunto de variables
        variables.remove(variableIzquierda);

        // Crear Scanner para pedir valores de variables al usuario
        Scanner scanner = new Scanner(System.in);

        // Obtener valores para cada variable a través de consola
        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);

        // Reemplazar, en 'input', cada variable por su propio nombre (para evitar conflictos)
        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getKey());
        }

        // Crear lista para almacenar datos temporales
        List<String> temporales = new ArrayList<>();
        // Crear lista para almacenar instrucciones ASM
        List<String> instruccionesASM = new ArrayList<>();

        // Procesar la expresión y generar operaciones intermedias
        procesarExpresion(input, temporales, instruccionesASM, valoresVariables);

        // Línea en blanco para separar en consola
        System.out.print("\n");

        // Mostrar en consola los registros temporales creados
        for (String temporal : temporales) {
            System.out.println(temporal);
        }

        // Obtener el resultado numérico calculado desde el mapa de variables
        double resultadoNumerico = valoresVariables.get(variableIzquierda);

        // Mostrar resultado con formato de 3 decimales (solo para debug/visualización)
        System.out.println("\n - Resultado numérico calculado: "
                           + String.format("%.3f", resultadoNumerico) + "\n");

        /*
         * =========================================
         * Creamos la variable "resultadoFinalJava"
         * (con signo '-' si es negativo).
         * =========================================
         */
        String resultadoFinalJava;
        if (resultadoNumerico < 0) {
            resultadoFinalJava = "-" + String.format("%.3f", Math.abs(resultadoNumerico));
        } else {
            resultadoFinalJava = String.format("%.3f", resultadoNumerico);
        }

        // Generar el archivo ASM con las instrucciones y la lógica deseada
        generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda, resultadoFinalJava);

        // Cerrar el Scanner
        scanner.close();
    }

    // Validar estructura de expresión
    private static boolean esExpresionValida(String expresion) {
        // Verificar que no haya operadores consecutivos
        Pattern operadoresConsecutivos = Pattern.compile("[+\\-*/=]{2,}");
        Matcher matcherOperadores = operadoresConsecutivos.matcher(expresion);
        if (matcherOperadores.find()) {
            return false; // Falla si encuentra operadores consecutivos
        }

        // Validar que no haya paréntesis sin operador antes (p.ej. a(b))
        Pattern parenSinOperador = Pattern.compile("(?<![+\\-*/(=])\\(");
        Matcher matcherParenSinOperador = parenSinOperador.matcher(expresion);
        if (matcherParenSinOperador.find()) {
            return false; // Falla si el paréntesis no tiene un operador antes
        }

        // Verificar que no haya números seguidos de '(' sin operador (p.ej. 5(3+2))
        Pattern numeroSinOperador = Pattern.compile("\\d+\\(");
        Matcher matcherNumeroSinOperador = numeroSinOperador.matcher(expresion);
        if (matcherNumeroSinOperador.find()) {
            return false; // Falla si un número va inmediatamente seguido de '('
        }

        // Verificar que haya exactamente un '='
        long countIgual = expresion.chars().filter(ch -> ch == '=').count();
        if (countIgual != 1) {
            return false; // Falla si no hay '=' o hay más de uno
        }

        // Validar que el lado izquierdo del '=' sea un identificador válido
        String[] partes = expresion.split("=", 2);
        String ladoIzquierdo = partes[0];
        if (!ladoIzquierdo.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return false; // Falla si no es un nombre de variable válido
        }

        // Verificar que no haya caracteres inválidos en la expresión
        Pattern caracteresInvalidos = Pattern.compile("[^a-zA-Z0-9_+\\-*/=().]");
        Matcher matcherCaracteresInvalidos = caracteresInvalidos.matcher(expresion);
        if (matcherCaracteresInvalidos.find()) {
            return false; // Falla si encuentra símbolos no permitidos
        }

        // Verificar balance de paréntesis
        int contadorParentesis = 0;
        for (char c : expresion.toCharArray()) {
            if (c == '(')
                contadorParentesis++;
            if (c == ')')
                contadorParentesis--;
            if (contadorParentesis < 0) {
                return false; // Falla si hay más ')' que '('
            }
        }
        if (contadorParentesis != 0) {
            return false; // Falla si no hay balance total
        }

        return true;
    }

    // Identificar variable en lado izquierdo del '='
    private static String identificarVariableIzquierda(String expresion) {
        int indiceIgual = expresion.indexOf('=');
        if (indiceIgual != -1) {
            return expresion.substring(0, indiceIgual);
        }
        return null;
    }

    // Identificar todas las variables en la expresión
    private static Set<String> identificarVariables(String expresion) {
        Set<String> variables = new HashSet<>();
        Pattern variablePattern = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
        Matcher matcher = variablePattern.matcher(expresion);

        while (matcher.find()) {
            variables.add(matcher.group());
        }

        return variables;
    }

    // Solicitar valores de las variables al usuario
    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        Map<String, Double> valoresVariables = new HashMap<>();
        Pattern patternVariable = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

        for (String variable : variables) {
            while (true) {
                System.out.print(" * Ingrese el valor para la variable '" + variable + "': ");
                String entrada = scanner.next();

                try {
                    double valor = Double.parseDouble(entrada);
                    valoresVariables.put(variable, valor);
                    break;
                } catch (NumberFormatException e) {
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

    // Procesar la expresión aritmética y generar temporales e instrucciones ASM
    private static String procesarExpresion(
        String expresion,
        List<String> temporales,
        List<String> instruccionesASM,
        Map<String, Double> valoresVariables
    ) {
        String[] operadoresJerarquia = { "\\(", "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores = { "PAREN", "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Procesar primero paréntesis de manera recursiva
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcherParentesis;
        while ((matcherParentesis = parentesisPattern.matcher(expresion)).find()) {
            String subExpresion = matcherParentesis.group(1);
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM, valoresVariables);
            expresion = expresion.replaceFirst(Pattern.quote("(" + subExpresion + ")"), temporal);
        }

        // Recorrer operadores en orden de precedencia (ya no PAREN)
        for (int i = 1; i < operadoresJerarquia.length; i++) {
            Pattern operacionPattern = Pattern.compile(
                "([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)"
                + operadoresJerarquia[i] +
                "([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)"
            );

            Matcher matcher;
            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                String operando1 = matcher.group(1);
                String operando2 = matcher.group(3);
                String temporal = "T" + temporalCounter++;

                // MOV
                if (nombresOperadores[i].equals("MOV")) {
                    double valor2 = valoresVariables.containsKey(operando2)
                            ? valoresVariables.get(operando2)
                            : Double.parseDouble(operando2);
                    valoresVariables.put(operando1, valor2);

                    String instruccionASM = generarInstruccionASM(nombresOperadores[i], operando1, operando2, temporal);
                    instruccionesASM.add(instruccionASM);
                    return operando1;
                }

                // Otras operaciones aritméticas
                double valor1 = valoresVariables.containsKey(operando1)
                        ? valoresVariables.get(operando1)
                        : Double.parseDouble(operando1);
                double valor2 = valoresVariables.containsKey(operando2)
                        ? valoresVariables.get(operando2)
                        : Double.parseDouble(operando2);

                double resultado = calcularResultado(valor1, valor2, nombresOperadores[i]);
                valoresVariables.put(temporal, resultado);

                String operacion = String.format("    %s -> %s, %s, %s", 
                                                 temporal, operando1, operando2, nombresOperadores[i]);
                temporales.add(operacion);

                String instruccionASM = generarInstruccionASM(nombresOperadores[i], operando1, operando2, temporal);
                instruccionesASM.add(instruccionASM);

                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        return expresion;
    }

    // Calcular operaciones en tiempo real
    private static double calcularResultado(double operando1, double operando2, String operador) {
        return switch (operador) {
            case "MUL" -> operando1 * operando2;
            case "DIV" -> operando1 / operando2;
            case "ADD" -> operando1 + operando2;
            case "SUB" -> operando1 - operando2;
            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        };
    }

    // Generar instrucción ASM con base en operador y operandos
    private static String generarInstruccionASM(String operador, String operando1, String operando2, String temporal) {
        StringBuilder instruccion = new StringBuilder();

        switch (operador) {
            case "MUL" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    MOV BX, %s", operando2)).append("\n");
                instruccion.append("    CWD\n");
                instruccion.append("    IMUL BX\n");
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }
            case "DIV" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append("    XOR DX, DX\n");
                instruccion.append(String.format("    MOV BX, %s", operando2)).append("\n");
                instruccion.append("    CWD\n");
                instruccion.append("    IDIV BX\n");
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }
            case "ADD" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    ADD AX, %s", operando2)).append("\n");
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }
            case "SUB" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    SUB AX, %s", operando2)).append("\n");
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }
            case "MOV" -> {
                instruccion.append(String.format("\n    MOV AX, %s", operando2)).append("\n");
                instruccion.append(String.format("    MOV %s, AX", operando1));
            }
            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        }

        return instruccion.toString();
    }

    /*
     * ========================================================================
     * Generar el archivo ASM.
     * Se declara ResFinal (por si es negativo),
     * Y definimos la lógica de SALTOS:
     *   - si <0 => NO imprime ASM, SÍ imprime ResFinal
     *   - si >=0 => imprime ASM, NO imprime ResFinal
     * ========================================================================
     */
    private static void generarArchivoASM(
        List<String> instruccionesASM,
        Map<String, Double> valoresVariables,
        String variableIzquierda,
        String resultadoFinalJava
    ) {
        try (FileWriter writer = new FileWriter("Resultado.ASM")) {

            // Configuración inicial
            writer.write(".MODEL SMALL\n");
            writer.write(".STACK 100h\n\n");
            writer.write(".DATA\n");

            // Declarar variables y su valor en .DATA
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                writer.write("    " + entry.getKey() + " DW "
                             + convertirValorASM(entry.getKey(), entry.getValue()) + "\n");
            }

            // Declaraciones originales
            writer.write("    Resultado DB '" + variableIzquierda + " =   $'\n");
            writer.write("    value DB 5 DUP('$') ;Buffer para el valor en texto\n\n");
            writer.write("    Punto  DB   '.  $'\n");
            writer.write("    value1 DB 5 DUP('$') ;Buffer para el valor en texto\n\n");

            // NUEVA variable para el resultado (incluye signo si es negativo)
            writer.write("    ResFinal DB \"" + resultadoFinalJava + "\", '$'\n\n");

            // Segmento de código
            writer.write(".CODE\n");
            writer.write("start:\n");
            writer.write("    MOV AX, @DATA\n");
            writer.write("    MOV DS, AX\n\n");

            // Instrucciones generadas para la expresión
            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            // --------------------------------------------------------
            // Comparamos el resultado con 0
            // --------------------------------------------------------
            writer.write("\n    ;------------------------------------------\n");
            writer.write("    ; Verificar si el resultado es negativo\n");
            writer.write("    ;------------------------------------------\n");
            writer.write("    MOV AX, " + variableIzquierda + "\n");
            writer.write("    CMP AX, 0\n");
            // Si AX < 0, salta a NEGATIVO
            writer.write("    JL NEGATIVO\n");

            // =======================================================
            //   CASO NO NEGATIVO (≥ 0): IMPRIME DESDE ASM
            // =======================================================
            writer.write("\nNO_NEGATIVO:\n");
            writer.write("    ;=============================================\n");
            writer.write("    ; Imprime parte entera y decimal (ASM normal)\n");
            writer.write("    ;=============================================\n");
            // Convertir parte entera
            writer.write("    MOV AX, " + variableIzquierda + "\n");
            writer.write("    MOV CX, 5\n");
            writer.write("    LEA DI, value\n");
            writer.write("    MOV BX, 10\n");
            writer.write("\nEnteros:\n");
            writer.write("    XOR DX, DX\n");
            writer.write("    DIV BX\n");
            writer.write("    ADD DL, '0'\n");
            writer.write("    DEC DI\n");
            writer.write("    MOV [DI], DL\n");
            writer.write("    DEC CX\n");
            writer.write("    TEST AX, AX\n");
            writer.write("    JNZ Enteros\n\n");

            // Mostrar "variableIzquierda = "
            writer.write("    LEA DX, Resultado\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n");

            // Mostrar la parte entera
            writer.write("    LEA DX, value\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Convertir parte decimal
            writer.write("    MOV AX, " + variableIzquierda + "_D\n");
            writer.write("    MOV CX, 5\n");
            writer.write("    LEA DI, value1\n");
            writer.write("    MOV BX, 10\n");
            writer.write("\nDecimales:\n");
            writer.write("    XOR DX, DX\n");
            writer.write("    DIV BX\n");
            writer.write("    ADD DL, '0'\n");
            writer.write("    DEC DI\n");
            writer.write("    MOV [DI], DL\n");
            writer.write("    DEC CX\n");
            writer.write("    TEST AX, AX\n");
            writer.write("    JNZ Decimales\n\n");

            // Imprimir punto decimal
            writer.write("    LEA DX, Punto\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n");

            // Mostrar parte decimal
            writer.write("    LEA DX, value1\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Listo, saltar a FIN
            writer.write("    JMP FIN\n");

            // =======================================================
            //   CASO NEGATIVO: NO imprime ASM, SÓLO imprime ResFinal
            // =======================================================
            writer.write("\nNEGATIVO:\n");
            writer.write("    ;=====================================\n");
            writer.write("    LEA DX, ResFinal\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Etiqueta de fin
            writer.write("FIN:\n");
            writer.write("    MOV AH, 4Ch\n");
            writer.write("    INT 21h\n");
            writer.write("END start\n");

            System.out.println(" - Archivo ASM generado exitosamente: Resultado.ASM\n");
        } catch (IOException e) {
            System.err.println(" - Error al generar el archivo ASM: " + e.getMessage());
        }
    }

    // Convertir valor double a formato ASM (parte entera y decimal)
    private static String convertirValorASM(String variable, double valor) {
        int parteEntera = (int) valor;
        int parteDecimal = (int) Math.round((valor - parteEntera) * 1000);

        String parteEnteraFormateada = String.format("%03d", parteEntera);
        String parteDecimalFormateada = String.format("%03d", parteDecimal);

        return String.format("%s\n    %s_D DW %s ;Decimales de " + variable,
                             parteEnteraFormateada, variable, parteDecimalFormateada);
    }
}
