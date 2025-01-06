
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;
import javax.swing.JFileChooser;

/**
 * Clase principal que procesa expresiones aritméticas desde un archivo .txt,
 * valida su estructura, y genera un archivo Ensamblador (ASM) con ofuscación
 * en la parte de impresión y manejo de resultados negativos.
 */
public class ExpresionesAritmeticasASM {
    // ---------------------------------------------------------------------------------
    // PROPIEDADES
    // ---------------------------------------------------------------------------------
    private static int temporalCounter = 1;
    // Tamaño por defecto para nombres random
    // Contador para variables temporales

    // ---------------------------------------------------------------------------------
    // MÉTODO PRINCIPAL
    // ---------------------------------------------------------------------------------
    @SuppressWarnings("ConvertToTryWithResources")
    public static void main(String[] args) {
        // Selector de archivos
        while (true) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Seleccione un archivo .txt");

            // Leer el archivo .txt
            int result = fileChooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION) {
                System.out.println("No se seleccionó ningún archivo.");
                return;
            }

            File selectedFile = fileChooser.getSelectedFile();
            if (!selectedFile.getName().endsWith(".txt")) {
                System.err.println("Por favor, seleccione un archivo con extensión .txt");
                continue;
            }

            // Cargar la expresión del archivo (sin espacios)
            String ExpresionAritmetica;
            try {
                String contenido = new String(Files.readAllBytes(Paths.get(selectedFile.getAbsolutePath())));
                ExpresionAritmetica = contenido.replaceAll("\\s+", "").toLowerCase(); // Convertir a minúsculas
            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + e.getMessage());
                return;
            }

            // Formatear la expresión para mostrarla con espacios
            String expresionFormateada = formatearExpresion(ExpresionAritmetica);

            // Eliminar los signos negativos de variables como "(-a)"
            ExpresionAritmetica = ExpresionAritmetica.replaceAll("\\(-([a-zA-Z_][a-zA-Z0-9_]*)\\)", "$1");

            // Mostrar la expresión original en la consola
            System.out.println("\nExpresión Aritmética Original: " + expresionFormateada + "\n");

            // Validar la expresión
            if (!esExpresionValida(ExpresionAritmetica)) {
                System.err.println(
                        "La expresión es inválida (operadores consecutivos, paréntesis mal, etc.). Por favor, seleccione un nuevo archivo.");
                continue;
            }

            // Si la expresión es válida, continuar con el procesamiento
            // Identificar variable izquierda y el resto de variables
            String variableIzquierda = identificarVariableIzquierda(ExpresionAritmetica);
            Set<String> variables = identificarVariables(ExpresionAritmetica);
            variables.remove(variableIzquierda);

            // Pedir valores de las variables
            Scanner scanner = new Scanner(System.in);
            Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);
            scanner.close();

            // Reemplazar las variables en la expresión con su propio nombre
            String input = ExpresionAritmetica;
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                input = input.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getKey());
            }

            // Generar estructuras para temporales e instrucciones ASM
            List<String> temporales = new ArrayList<>();
            List<String> instruccionesASM = new ArrayList<>();

            // Procesar la expresión y generar ASM
            procesarExpresion(input, temporales, instruccionesASM, valoresVariables);

            // Mostrar en consola las operaciones intermedias
            System.out.println();
            for (String temp : temporales) {
                System.out.println(temp);
            }

            // Resultado calculado en Java
            double resultadoNumerico = valoresVariables.get(variableIzquierda);

            // Mostrar el resultado numérico en consola con Punto decimal
            System.out.println("\n - Resultado: " + variableIzquierda + " = "
                    + String.format(Locale.US, "%.3f", resultadoNumerico) + "\n");

            // Definir la cadena que representará el resultado en ASM con Punto decimal
            String resultadoFinalJava = (resultadoNumerico < 0)
                    ? "-" + String.format(Locale.US, "%.3f", Math.abs(resultadoNumerico))
                    : String.format(Locale.US, "%.3f", resultadoNumerico);

            // Generar el archivo ASM
            try {
                generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda, resultadoFinalJava,
                        expresionFormateada);
                System.out.println(" - Archivo ASM generado exitosamente: Resultado.ASM\n");
            } catch (IOException e) {
                System.err.println(" - Error al generar el archivo ASM: " + e.getMessage());
            }

            // Salir del bucle si todo es válido y se procesa correctamente
            break;
        }
    }

    /**
     * Formatea la expresión aritmética para agregar espacios alrededor de
     * operadores y asegurar que los paréntesis externos tengan un espacio.
     * Además, elimina los espacios entre '(' y '-' en expresiones como "(-a)" o
     * "(-1)".
     */
    private static String formatearExpresion(String expresion) {
        // Agregar espacios alrededor de los operadores
        expresion = expresion.replaceAll("(?<=[^\\s+\\-*/=])([+\\-*/=])(?=[^\\s+\\-*/=])", " $1 ");

        // Eliminar espacios en expresiones del tipo "z=( - a)" o "z=( - 1)"
        expresion = expresion.replaceAll("\\(\\s*-\\s*", "(-");

        return expresion.trim();
    }

    // ---------------------------------------------------------------------------------
    // VALIDACIÓN DE LA EXPRESIÓN
    // ---------------------------------------------------------------------------------
    /**
     * Valida la estructura global de una expresión, revisando:
     * - Solo un '='
     * - No operadores consecutivos
     * - Paréntesis balanceados
     * - Identificador válido a la izquierda
     * - Sin caracteres no permitidos
     */
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

        // Verificar que no haya números seguidos de identificadores (p.ej. 6x)
        Pattern numeroSeguidoIdentificador = Pattern.compile("\\d+[a-zA-Z_]");
        Matcher matcherNumeroSeguidoIdentificador = numeroSeguidoIdentificador.matcher(expresion);
        if (matcherNumeroSeguidoIdentificador.find()) {
            return false; // Falla si un número va inmediatamente seguido de un identificador
        }

        // Verificar que haya exactamente un '='
        long countIgual = expresion.chars().filter(ch -> ch == '=').count();
        if (countIgual != 1) {
            return false; // Falla si no hay '=' o hay más de uno
        }

        // Validar que el lado izquierdo del '=' sea un identificador válido
        String[] partes = expresion.split("=", 2);
        String ladoIzquierdo = partes[0].trim();
        String ladoDerecho = partes[1].trim();
        if (!ladoIzquierdo.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return false; // Falla si no es un nombre de variable válido
        }

        // Verificar que el lado izquierdo no esté en el lado derecho
        Pattern variableLadoIzquierdo = Pattern.compile("\\b" + Pattern.quote(ladoIzquierdo) + "\\b");
        Matcher matcherVariableLadoIzquierdo = variableLadoIzquierdo.matcher(ladoDerecho);
        if (matcherVariableLadoIzquierdo.find()) {
            return false; // Falla si la variable izquierda aparece en el lado derecho
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

        // Verificar que no haya variables internas como T1, T1_D, etc.
        Pattern variablesInternas = Pattern.compile("\\bT\\d+(_D)?\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcherVariablesInternas = variablesInternas.matcher(expresion);
        if (matcherVariablesInternas.find()) {
            return false; // Falla si encuentra variables internas
        }

        // Verificar que no se usen palabras reservadas
        String[] palabrasReservadas = { "ExpresionAritmetica", "Resultado", "Signo", "Enteros", "Punto", "Decimales",
                "start" };
        for (String palabraReservada : palabrasReservadas) {
            Pattern patronReservada = Pattern.compile("\\b" + Pattern.quote(palabraReservada) + "\\b",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcherReservada = patronReservada.matcher(expresion);
            if (matcherReservada.find()) {
                return false; // Falla si encuentra una palabra reservada
            }
        }

        // Si llega aquí, la expresión es válida
        return contadorParentesis == 0;
    }

    /**
     * Separa la parte izquierda del '=' para obtener la variable asignada.
     */
    private static String identificarVariableIzquierda(String expresion) {
        int idx = expresion.indexOf('=');
        return (idx != -1) ? expresion.substring(0, idx) : null;
    }

    /**
     * Identifica todas las variables dentro de la expresión (a-z, 0-9 y '_').
     * En el caso de expresiones como "(-a)", se elimina el signo negativo para
     * evitar errores.
     */
    private static Set<String> identificarVariables(String expresion) {
        Set<String> vars = new HashSet<>();
        Matcher m = Pattern.compile("\\(-([a-zA-Z_][a-zA-Z0-9_]*)\\)").matcher(expresion);
        while (m.find()) {
            vars.add(m.group(1)); // Agregar solo el nombre de la variable sin el signo negativo
        }

        // Buscar también variables que no estén precedidas por "(-)"
        m = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*").matcher(expresion);
        while (m.find()) {
            vars.add(m.group());
        }

        return vars;
    }

    // ---------------------------------------------------------------------------------
    // PEDIR VALORES PARA LAS VARIABLES DESDE CONSOLA
    // ---------------------------------------------------------------------------------
    /**
     * Pide al usuario los valores numéricos para cada variable.
     */
    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        Map<String, Double> map = new HashMap<>();
        for (String var : variables) {
            while (true) {
                System.out.print(" * Ingrese el valor de '" + var + "': ");
                String entrada = scanner.next();
                try {
                    double val = Double.parseDouble(entrada);
                    map.put(var, val);
                    break;
                } catch (NumberFormatException ex) {
                    System.out.println("Error: Solo se permiten números.");
                }
            }
        }
        return map;
    }

    // ---------------------------------------------------------------------------------
    // PROCESAR LA EXPRESIÓN Y GENERAR INSTRUCCIONES ASM
    // ---------------------------------------------------------------------------------
    private static String procesarExpresion(
            String expresion,
            List<String> temporales,
            List<String> instruccionesASM,
            Map<String, Double> valoresVariables) {
        String[] operadoresJerarquia = { "\\(", "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores = { "PAREN", "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Primero procesamos paréntesis de manera recursiva
        Pattern patParentesis = Pattern.compile("\\(([^()]+)\\)");
        Matcher matParen;
        while ((matParen = patParentesis.matcher(expresion)).find()) {
            String subExp = matParen.group(1);
            String temp = procesarExpresion(subExp, temporales, instruccionesASM, valoresVariables);
            expresion = expresion.replaceFirst(Pattern.quote("(" + subExp + ")"), temp);
        }

        // Luego iteramos operadores en orden
        for (int i = 1; i < operadoresJerarquia.length; i++) {
            String regex = String.format(
                    "([a-zA-Z_][a-zA-Z0-9_]*|\\-?\\d+(\\.\\d+)?|T\\d+)%s([a-zA-Z_][a-zA-Z0-9_]*|\\-?\\d+(\\.\\d+)?|T\\d+)",
                    operadoresJerarquia[i]);

            Pattern patOp = Pattern.compile(regex);
            Matcher m;
            while ((m = patOp.matcher(expresion)).find()) {
                String op1 = m.group(1);
                String op2 = m.group(3);
                String tempVar = "T" + (temporalCounter++);

                if (nombresOperadores[i].equals("MOV")) {
                    double val2 = valoresVariables.containsKey(op2)
                            ? valoresVariables.get(op2)
                            : Double.parseDouble(op2);
                    valoresVariables.put(op1, val2);

                    String asm = generarInstruccionASM("MOV", op1, op2, tempVar);
                    instruccionesASM.add(asm);
                    return op1;
                }

                double v1 = valoresVariables.containsKey(op1)
                        ? valoresVariables.get(op1)
                        : Double.parseDouble(op1);
                double v2 = valoresVariables.containsKey(op2)
                        ? valoresVariables.get(op2)
                        : Double.parseDouble(op2);

                double r = calcularResultado(v1, v2, nombresOperadores[i]);
                valoresVariables.put(tempVar, r);

                // Calcula el resultado de la operación
                double resultadoTemporal = calcularResultado(v1, v2, nombresOperadores[i]);

                // Almacena el valor calculado para el temporal en el mapa
                valoresVariables.put(tempVar, resultadoTemporal);

                // Formatea el valor del resultado en el formato 000.000
                String resultadoFormateado = String.format(Locale.US, "%.3f", resultadoTemporal);

                // Incluye el valor del temporal en la operación
                String operacion = String.format("    %s -> %s, %s, %s = %s", tempVar, op1, op2, nombresOperadores[i],
                        resultadoFormateado);
                temporales.add(operacion);

                String asm = generarInstruccionASM(nombresOperadores[i], op1, op2, tempVar);
                instruccionesASM.add(asm);

                expresion = expresion.replaceFirst(Pattern.quote(m.group(0)), tempVar);
            }
        }

        return expresion;
    }

    /**
     * Realiza la operación aritmética entre dos operandos, en Java,
     * para luego almacenar el resultado en un "temporal".
     */
    private static double calcularResultado(double a, double b, String op) {
        return switch (op) {
            case "MUL" -> a * b;
            case "DIV" -> a / b;
            case "ADD" -> a + b;
            case "SUB" -> a - b;
            default -> throw new IllegalArgumentException("Operador no soportado: " + op);
        };
    }

    private static String procesarOperando(String operando) {
        // Caso: Variable, temporal o identificador
        if (operando.matches("[a-zA-Z_][a-zA-Z0-9_]*|T\\d+")) {
            return operando + "_D"; // Agregar sufijo "_D"
        }

        if (operando.matches("\\-?\\d+\\.\\d+")) { // Número decimal negativo o positivo
            String[] partes = operando.split("\\.");
            return partes[1];
        }
        if (operando.matches("\\-?\\d+")) { // Número entero negativo o positivo
            return operando.startsWith("-") ? "000" : "000"; // Procesar como 000 o similar
        }

        // Por defecto, devolver el operando tal cual (nunca debería llegar aquí)
        return operando;
    }

    private static String generarInstruccionASM(String operador, String op1, String op2, String tempVar) {
        StringBuilder sb = new StringBuilder();

        switch (operador) {
            case "MUL" -> {
                sb.append("\n    ;Multiplicación\n");
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append(String.format("    MOV BX, %s", op2)).append("\n");
                sb.append("    CWD\n");
                sb.append("    IMUL BX\n");
                sb.append(String.format("    MOV %s, AX", tempVar)).append("\n");
                sb.append("    ;Multiplicación_D\n");
                sb.append(String.format("    MOV AX, %s", procesarOperando(op1))).append("\n");
                sb.append(String.format("    MOV BX, %s", procesarOperando(op2))).append("\n");
                sb.append("    CWD\n");
                sb.append("    IMUL BX\n");
                sb.append(String.format("    MOV %s_D, AX", tempVar));
            }
            case "DIV" -> {
                sb.append("\n    ;División\n");
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append("    XOR DX, DX\n");
                sb.append(String.format("    MOV BX, %s", op2)).append("\n");
                sb.append("    CWD\n");
                sb.append("    IDIV BX\n");
                sb.append(String.format("    MOV %s, AX", tempVar)).append("\n");
                sb.append("    ;División_D\n");
                sb.append(String.format("    MOV AX, %s", procesarOperando(op1))).append("\n");
                sb.append(String.format("    MOV BX, %s", procesarOperando(op2))).append("\n");
                sb.append("    CWD\n");
                sb.append("    IDIV BX\n");
                sb.append(String.format("    MOV %s_D, AX", tempVar));
            }
            case "ADD" -> {
                sb.append("\n    ;Suma\n");
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append(String.format("    ADD AX, %s", op2)).append("\n");
                sb.append(String.format("    MOV %s, AX", tempVar)).append("\n");
                sb.append("    ;Suma_D\n");
                sb.append(String.format("    MOV AX, %s", procesarOperando(op1))).append("\n");
                sb.append(String.format("    ADD AX, %s", procesarOperando(op2))).append("\n");
                sb.append(String.format("    MOV %s_D, AX", tempVar));
            }
            case "SUB" -> {
                sb.append("\n    ;Resta\n");
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append(String.format("    SUB AX, %s", op2)).append("\n");
                sb.append(String.format("    MOV %s, AX", tempVar)).append("\n");
                sb.append("    ;Resta_D\n");
                sb.append(String.format("    MOV AX, %s", procesarOperando(op1))).append("\n");
                sb.append(String.format("    SUB AX, %s", procesarOperando(op2))).append("\n");
                sb.append(String.format("    MOV %s_D, AX", tempVar));
            }
            case "MOV" -> {
                sb.append("\n    ;Asignación\n");
                sb.append(String.format("    MOV AX, %s", op2)).append("\n");
                sb.append(String.format("    MOV %s, AX", op1)).append("\n");
                sb.append("    ;Asignación_D\n");
                sb.append(String.format("    MOV AX, %s", procesarOperando(op2))).append("\n");
                sb.append(String.format("    MOV %s, AX", procesarOperando(op1)));
            }
            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        }

        return sb.toString();
    }

    private static void generarArchivoASM(
            List<String> instruccionesASM,
            Map<String, Double> valoresVariables,
            String variableIzquierda,
            String resultadoFinalJava,
            String expresionFormateada) throws IOException {
        try (FileWriter writer = new FileWriter("Resultado.ASM")) {
            // Separar las partes del resultado
            char Signo = resultadoFinalJava.charAt(0) == '-' ? '-' : '+';
            String[] partes = resultadoFinalJava.replace("-", "").split("\\.");
            String parteEntera = partes[0];
            String parteDecimal = partes.length > 1 ? partes[1] : "000";

            // 1) Encabezado
            agregarEncabezado(writer);

            // 2) Declarar variables en .DATA
            if (variableIzquierda != null) {
                writer.write("    " + variableIzquierda + " DW ?\n");
                writer.write("    " + variableIzquierda + "_D DW ? ;Decimales de '" + variableIzquierda + "'\n\n");
            }

            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                String declaracion = convertirValorASM(entry.getKey(), entry.getValue(), variableIzquierda);
                if (!declaracion.isEmpty()) {
                    writer.write("    " + declaracion);
                }
            }

            writer.write("\n");

            for (int i = 1; i < temporalCounter; i++) {
                writer.write("    T" + i + " DW ?\n");
                writer.write("    T" + i + "_D DW ? ;Decimales de 'T" + i + "'\n");
            }

            writer.write("\n    ExpresionAritmetica DB '" + expresionFormateada + "', 0Dh, 0Ah, 0Dh, 0Ah, '$'\n");

            // Declarar las variables con sus valores para imprimir (excluyendo temporales y
            // variableIzquierda)
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                String nombreVariable = entry.getKey();
                if (!nombreVariable.equals(variableIzquierda) && !nombreVariable.startsWith("T")) {
                    double valor = entry.getValue();
                    String valorFormateado = String.format(Locale.US, "%.3f", valor);
                    writer.write("    " + nombreVariable + "_T DB '  " + nombreVariable + " = " + valorFormateado
                            + "', 0Dh, 0Ah, '$'\n");
                }
            }

            writer.write("    Resultado DB 0Dh, 0Ah, '" + variableIzquierda + " = ', '$'\n");
            writer.write("    Signo DB " + convertirCadenaADecimal(String.valueOf(Signo)) + ", 5 DUP('$')\n");
            writer.write("    Enteros DB " + convertirCadenaADecimal(parteEntera) + ", 5 DUP('$')\n");
            writer.write("    Punto DB '.', '$'\n");
            writer.write("    Decimales DB " + convertirCadenaADecimal(parteDecimal) + ", 5 DUP('$')\n\n");

            // 3) Segmento de código
            agregarSegmentoCodigoInicio(writer);

            // Imprimir la expresión aritmética
            writer.write("    ;Imprimir Expresión Aritmetica\n");
            writer.write("    LEA DX, ExpresionAritmetica\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Imprimir los valores de las variables (excluyendo temporales y
            // variableIzquierda)
            writer.write("    ;Imprimir variables\n");
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                String nombreVariable = entry.getKey();
                if (!nombreVariable.equals(variableIzquierda) && !nombreVariable.startsWith("T")) {
                    writer.write("    LEA DX, " + nombreVariable + "_T\n");
                    writer.write("    MOV AH, 09h\n");
                    writer.write("    INT 21h\n");
                }
            }

            // 4) Generar las instrucciones ASM
            for (String instruccion : instruccionesASM) {
                // Reemplazar el Punto por un Punto y coma en los valores numéricos
                String instruccionModificada = instruccion.replaceAll("(\\d+)\\.(\\d+)", "$1;$2");
                writer.write("    " + instruccionModificada + "\n");
            }

            // Imprimir el resultado desde las partes separadas
            writer.write("\n    ;Imprimir resultado\n");
            writer.write("    LEA DX, Resultado\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Conversión de la parte entera
            writer.write("    ;Conversión de '" + variableIzquierda + "' a texto (Enteros)\n");
            writer.write("    MOV AX, " + variableIzquierda + "\n");
            writer.write("    MOV CX, 5\n");
            writer.write("    LEA DI, " + variableIzquierda + "\n");
            writer.write("    MOV BX, 10\n\n");

            writer.write("    LOOP_Enteros:\n");
            writer.write("        XOR DX, DX\n");
            writer.write("        DIV BX\n");
            writer.write("        ADD DL, '0'\n");
            writer.write("        DEC DI\n");
            writer.write("        MOV [DI], DL\n");
            writer.write("        DEC CX\n");
            writer.write("        TEST AX, AX\n");
            writer.write("        JNZ LOOP_Enteros\n\n");

            // Imprimir el signo
            writer.write("    ;Imprimir signo\n");
            writer.write("    LEA DX, Signo\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Imprimir la parte entera
            writer.write("    ;Imprimir parte entera\n");
            writer.write("    LEA DX, Enteros\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Conversión de la parte decimal
            writer.write("    ;Conversión de '" + variableIzquierda + "_D' a texto (Decimales)\n");
            writer.write("    MOV AX, " + variableIzquierda + "_D\n");
            writer.write("    MOV CX, 5\n");
            writer.write("    LEA DI, " + variableIzquierda + "_D\n");
            writer.write("    MOV BX, 10\n\n");

            writer.write("    LOOP_Decimales:\n");
            writer.write("        XOR DX, DX\n");
            writer.write("        DIV BX\n");
            writer.write("        ADD DL, '0'\n");
            writer.write("        DEC DI\n");
            writer.write("        MOV [DI], DL\n");
            writer.write("        DEC CX\n");
            writer.write("        TEST AX, AX\n");
            writer.write("        JNZ LOOP_Decimales\n\n");

            // Imprimir el punto
            writer.write("    ;Imprimir punto\n");
            writer.write("    LEA DX, Punto\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Imprimir la parte decimal
            writer.write("    ;Imprimir parte decimal\n");
            writer.write("    LEA DX, Decimales\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // 6) Fin del programa
            writer.write("    MOV AH, 4Ch\n");
            writer.write("    INT 21h\n");
            writer.write("END start\n");
        }
    }

    /**
     * Convierte una cadena de caracteres en su representación decimal separada por
     * comas.
     *
     * @param cadena La cadena de entrada a convertir.
     * @return Una cadena con los valores decimales ASCII separados por comas.
     */
    private static String convertirCadenaADecimal(String cadena) {
        StringBuilder decimal = new StringBuilder();
        for (char c : cadena.toCharArray()) {
            decimal.append(String.format("%d,", (int) c));
        }
        // Eliminar la última coma
        if (decimal.length() > 0) {
            decimal.setLength(decimal.length() - 1);
        }
        return decimal.toString();
    }

    // ---------------------------------------------------------------------------------
    // MÉTODOS AUXILIARES
    // ---------------------------------------------------------------------------------

    /**
     * Agrega el encabezado .MODEL, .STACK, etc. al FileWriter.
     */
    private static void agregarEncabezado(FileWriter writer) throws IOException {
        writer.write(".MODEL SMALL\n");
        writer.write(".STACK 100h\n\n");
        writer.write(".DATA\n");
    }

    /**
     * Agrega la sección .CODE, la directiva start y la inicialización de DS.
     */
    private static void agregarSegmentoCodigoInicio(FileWriter writer) throws IOException {
        writer.write(".CODE\n");
        writer.write("start:\n");
        writer.write("    MOV AX, @DATA\n");
        writer.write("    MOV DS, AX\n\n");
    }

    /**
     * Convierte un valor double a una representación adecuada en ASM, excluyendo
     * temporales y la variable izquierda.
     *
     * @param variable          Nombre de la variable (se omitirán temporales y la
     *                          variable izquierda).
     * @param valor             Valor numérico asociado a la variable.
     * @param variableIzquierda La variable izquierda que debe ser omitida.
     * @return Una cadena formateada con las declaraciones ASM correspondientes,
     *         o una cadena vacía si la variable debe ser omitida.
     */
    private static String convertirValorASM(String variable, double valor, String variableIzquierda) {
        // Omitir temporales (prefijos "T") y la variable izquierda
        if (variable.startsWith("T") || variable.equalsIgnoreCase(variableIzquierda)) {
            return ""; // No generar nada para temporales o la variable izquierda
        }

        // Convertir el valor a una cadena con formato explícito usando Locale.US
        String resultado = String.format(Locale.US, "%.3f", valor);
        String[] partes = resultado.split("\\."); // Separar parte entera y decimal por el punto

        // Convertir las partes a enteros para su procesamiento
        int parteEntera = Integer.parseInt(partes[0]);
        int parteDecimal = Integer.parseInt(partes[1]); // Tomar todos los tres dígitos decimales

        // Generar salida en formato ASM
        StringBuilder sb = new StringBuilder();
        sb.append(variable).append(" DW ").append(parteEntera).append("\n");
        sb.append("    ").append(variable).append("_D DW ").append(String.format("%03d", parteDecimal));
        sb.append(" ;Decimales de '").append(variable).append("'\n");

        return sb.toString();
    }
}
