
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

        // Reemplazar, en 'input', cada variable por su propio nombre (para evitar
        // conflictos)
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
        System.out.println("\n - Resultado numérico calculado: " + String.format("%.3f", resultadoNumerico) + "\n");

        // Generar archivo ASM final con las instrucciones
        generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda);

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

        // Si llega aquí, la expresión es válida
        return true;
    }

    // Identificar variable en lado izquierdo del '='
    private static String identificarVariableIzquierda(String expresion) {
        // Obtener posición de '='
        int indiceIgual = expresion.indexOf('=');
        if (indiceIgual != -1) {
            // Extraer cadena hasta la posición de '='
            return expresion.substring(0, indiceIgual);
        }
        return null; // Retornar null si no se encuentra '='
    }

    // Identificar todas las variables en la expresión
    private static Set<String> identificarVariables(String expresion) {
        // Crear conjunto para almacenar variables
        Set<String> variables = new HashSet<>();
        // Expresión regular para detectar variables (inicia con letra o '_')
        Pattern variablePattern = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
        Matcher matcher = variablePattern.matcher(expresion);

        // Mientras haya coincidencias en la expresión
        while (matcher.find()) {
            // Agregar cada variable al conjunto
            variables.add(matcher.group());
        }

        return variables; // Retornar conjunto de variables
    }

    // Solicitar valores de las variables al usuario
    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        // Crear mapa para almacenar nombre de variable y su valor
        Map<String, Double> valoresVariables = new HashMap<>();

        // Expresión regular para validar nombres de variable
        Pattern patternVariable = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

        // Recorrer cada variable en el conjunto
        for (String variable : variables) {
            // Ciclo para pedir valor hasta que sea válido
            while (true) {
                // Pedir valor al usuario
                System.out.print(" * Ingrese el valor para la variable '" + variable + "': ");
                String entrada = scanner.next();

                // Intentar convertir a número
                try {
                    double valor = Double.parseDouble(entrada);
                    // Almacenar en el mapa si se convierte correctamente
                    valoresVariables.put(variable, valor);
                    break; // Salir del ciclo si el valor es válido
                } catch (NumberFormatException e) {
                    // Si no es número, verificar si es un nombre de variable
                    if (patternVariable.matcher(entrada).matches()) {
                        System.out.println("Error: No se permite usar el nombre de otra variable como valor.");
                    } else {
                        System.out.println("Error: Entrada no válida. Solo se permiten números.");
                    }
                }
            }
        }

        // Retornar mapa con valores ingresados
        return valoresVariables;
    }

    // Procesar la expresión aritmética y generar temporales e instrucciones ASM
    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM,
            Map<String, Double> valoresVariables) {
        // Definir jerarquía de operadores (orden de precedencia)
        String[] operadoresJerarquia = { "\\(", "\\*", "/", "\\+", "-", "=" };
        // Definir nombres de operadores (etiquetas para el switch interno)
        String[] nombresOperadores = { "PAREN", "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Procesar primero los paréntesis con expresión regular para encontrar su
        // contenido
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcherParentesis;
        // Mientras encuentre subexpresiones entre paréntesis
        while ((matcherParentesis = parentesisPattern.matcher(expresion)).find()) {
            // Obtener la subexpresión interna
            String subExpresion = matcherParentesis.group(1);
            // Procesar subexpresión de forma recursiva
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM, valoresVariables);
            // Reemplazar la subexpresión original con el resultado temporal
            expresion = expresion.replaceFirst(Pattern.quote("(" + subExpresion + ")"), temporal);
        }

        // Recorrer la lista de operadores en orden de precedencia (excepto 'PAREN' que
        // ya se procesó)
        for (int i = 1; i < operadoresJerarquia.length; i++) {
            // Crear patrón para buscar estructura: (operando1) (operador) (operando2)
            Pattern operacionPattern = Pattern.compile(
                    "([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)"
                            + operadoresJerarquia[i]
                            + "([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)");

            Matcher matcher;
            // Mientras se cumpla la expresión (operando1 operador operando2)
            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                // Capturar operando 1
                String operando1 = matcher.group(1);
                // Capturar operando 2
                String operando2 = matcher.group(3);
                // Crear nombre de variable temporal
                String temporal = "T" + temporalCounter++;

                // Manejar operación de asignación (MOV)
                if (nombresOperadores[i].equals("MOV")) {
                    // Obtener valor de operando2 (variable o número)
                    double valor2 = valoresVariables.containsKey(operando2)
                            ? valoresVariables.get(operando2)
                            : Double.parseDouble(operando2);

                    // Asignar valor2 a la variable operando1
                    valoresVariables.put(operando1, valor2);

                    // Generar instrucción MOV en ASM
                    String instruccionASM = generarInstruccionASM(nombresOperadores[i], operando1, operando2, temporal);
                    instruccionesASM.add(instruccionASM);

                    // Devolver la variable del lado izquierdo como resultado final
                    return operando1;
                }

                // Si no es operación MOV, realizar el cálculo aritmético
                double valor1 = valoresVariables.containsKey(operando1)
                        ? valoresVariables.get(operando1)
                        : Double.parseDouble(operando1);
                double valor2 = valoresVariables.containsKey(operando2)
                        ? valoresVariables.get(operando2)
                        : Double.parseDouble(operando2);

                // Calcular resultado en tiempo real
                double resultado = calcularResultado(valor1, valor2, nombresOperadores[i]);

                // Almacenar el valor resultante en el mapa con clave = 'temporal'
                valoresVariables.put(temporal, resultado);

                // Crear descripción de la operación para la lista de temporales
                String operacion = String.format("    %s -> %s, %s, %s", temporal, operando1, operando2,
                        nombresOperadores[i]);
                temporales.add(operacion);

                // Generar instrucción ASM para la operación
                String instruccionASM = generarInstruccionASM(nombresOperadores[i], operando1, operando2, temporal);
                instruccionesASM.add(instruccionASM);

                // Reemplazar la subexpresión completa con el nuevo temporal
                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        // Retornar la expresión final, que podría ser un único temporal
        return expresion;
    }

    // Método auxiliar para calcular operaciones en tiempo real
    private static double calcularResultado(double operando1, double operando2, String operador) {
        // Utilizar 'switch' mejorado (java 14+) para determinar la operación
        return switch (operador) {
            case "MUL" -> operando1 * operando2; // Multiplicación
            case "DIV" -> operando1 / operando2; // División
            case "ADD" -> operando1 + operando2; // Suma
            case "SUB" -> operando1 - operando2; // Resta
            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        };
    }

    // Generar instrucción ASM con base en operador y operandos
    private static String generarInstruccionASM(String operador, String operando1, String operando2, String temporal) {
        // Crear objeto StringBuilder para armar la instrucción
        StringBuilder instruccion = new StringBuilder();

        switch (operador) {
            case "MUL" -> {
                // Cargar operando1 en AX y operando2 en BX
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                instruccion.append(String.format("    MOV BX, %s", operando2)).append("\n");
                // Preparar registro para multiplicación
                instruccion.append("    CWD BX\n");
                // Instrucción de multiplicación (con signo)
                instruccion.append("    IMUL BX\n");
                // Almacenar el resultado en variable temporal
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }

            case "DIV" -> {
                // Cargar operando1 en AX
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                // Limpiar DX para división
                instruccion.append("    XOR DX, DX\n");
                // Cargar operando2 en BX
                instruccion.append(String.format("    MOV BX, %s", operando2)).append("\n");
                // Preparar registro para división
                instruccion.append("    CWD BX\n");
                // Instrucción de división con signo
                instruccion.append("    IDIV BX\n");
                // Almacenar el resultado en variable temporal
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }

            case "ADD" -> {
                // Cargar operando1 en AX
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                // Realizar suma con operando2
                instruccion.append(String.format("    ADD AX, %s", operando2)).append("\n");
                // Guardar resultado en variable temporal
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }

            case "SUB" -> {
                // Cargar operando1 en AX
                instruccion.append(String.format("\n    MOV AX, %s", operando1)).append("\n");
                // Restar operando2
                instruccion.append(String.format("    SUB AX, %s", operando2)).append("\n");
                // Guardar resultado en variable temporal
                instruccion.append(String.format("    MOV %s, AX", temporal));
            }

            case "MOV" -> {
                // Cargar operando2 en AX
                instruccion.append(String.format("\n    MOV AX, %s", operando2)).append("\n");
                // Mover contenido de AX a operando1
                instruccion.append(String.format("    MOV %s, AX", operando1));
            }

            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        }

        // Retornar la instrucción ASM como cadena
        return instruccion.toString();
    }

    // Generar el archivo ASM final
    private static void generarArchivoASM(List<String> instruccionesASM, Map<String, Double> valoresVariables,
            String variableIzquierda) {
        // Usar FileWriter dentro de try-with-resources para cerrar automáticamente
        try (FileWriter writer = new FileWriter("Resultado.ASM")) {

            // Escribir configuración inicial del archivo ASM
            writer.write(".MODEL SMALL\n");
            writer.write(".STACK 100h\n\n");
            writer.write(".DATA\n");

            // Declarar variables y su valor en .DATA, usando el método convertirValorASM
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                writer.write(
                        "    " + entry.getKey() + " DW " + convertirValorASM(entry.getKey(), entry.getValue()) + "\n");
            }

            // Declarar cadenas y buffers necesarios
            writer.write("    Resultado DB '" + variableIzquierda + " =   $'\n");
            writer.write("    value DB 5 DUP('$') ;Buffer para el valor en texto\n\n");
            writer.write("    Punto  DB   '.  $'\n");
            writer.write("    value1 DB 5 DUP('$') ;Buffer para el valor en texto\n\n");

            // Comenzar segmento de código
            writer.write(".CODE\n");
            writer.write("start:\n");

            // Inicializar segmento de datos
            writer.write("    MOV AX, @DATA\n");
            writer.write("    MOV DS, AX\n");

            // Escribir las instrucciones ASM que se generaron en el procesamiento
            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            // Rutina para convertir la parte entera de la variable en texto y mostrar
            writer.write("\n    ;Convertir " + variableIzquierda + " a texto\n");
            writer.write("    MOV AX, " + variableIzquierda + "\n");
            writer.write("    MOV CX, 5\n");
            writer.write("    LEA DI, value\n");
            writer.write("    MOV BX, 10\n\n");

            writer.write("Enteros:\n");
            writer.write("    XOR DX, DX\n");
            writer.write("    DIV BX\n");
            writer.write("    ADD DL, '0'\n");
            writer.write("    DEC DI\n");
            writer.write("    MOV [DI], DL\n");
            writer.write("    DEC CX\n");
            writer.write("    TEST AX, AX\n");
            writer.write("    JNZ Enteros\n\n");

            // Imprimir la cadena que indica el resultado
            writer.write("    LEA DX, Resultado\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n");

            // Imprimir el valor de la parte entera
            writer.write("    LEA DX, value\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Rutina para convertir la parte decimal de la variable en texto y mostrar
            writer.write("\n    ;Convertir " + variableIzquierda + "_D a texto\n");
            writer.write("    MOV AX, " + variableIzquierda + "_D\n");
            writer.write("    MOV CX, 5\n");
            writer.write("    LEA DI, value1\n");
            writer.write("    MOV BX, 10\n\n");

            writer.write("Decimales:\n");
            writer.write("    XOR DX, DX\n");
            writer.write("    DIV BX\n");
            writer.write("    ADD DL, '0'\n");
            writer.write("    DEC DI\n");
            writer.write("    MOV [DI], DL\n");
            writer.write("    DEC CX\n");
            writer.write("    TEST AX, AX\n");
            writer.write("    JNZ Decimales\n\n");

            // Imprimir el punto decimal
            writer.write("    LEA DX, Punto\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n");

            // Imprimir la parte decimal
            writer.write("    LEA DX, value1\n");
            writer.write("    MOV AH, 09h\n");
            writer.write("    INT 21h\n\n");

            // Terminar ejecución en DOS
            writer.write("    MOV AH, 4Ch\n");
            writer.write("    INT 21h\n");
            writer.write("END start\n");

            // Mensaje en consola si se genera el archivo con éxito
            System.out.println(" - Archivo ASM generado exitosamente: Resultado.ASM\n");
        } catch (IOException e) {
            // Imprimir error en caso de fallo al escribir
            System.err.println(" - Error al generar el archivo ASM: " + e.getMessage());
        }
    }

    // Convertir valor double a formato para ASM, separando parte entera y decimal
    private static String convertirValorASM(String variable, double valor) {
        // Obtener parte entera
        int parteEntera = (int) valor;
        // Multiplicar el decimal por 1000 y redondear
        int parteDecimal = (int) Math.round((valor - parteEntera) * 1000);

        // Formatear parte entera y parte decimal a 3 dígitos
        String parteEnteraFormateada = String.format("%03d", parteEntera);
        String parteDecimalFormateada = String.format("%03d", parteDecimal);

        // Devolver el bloque de declaración (parte entera y decimal) para ASM
        return String.format("%s\n    %s_D DW %s ;Decimales de " + variable,
                parteEnteraFormateada, variable, parteDecimalFormateada);
    }
}
