import java.lang.reflect.Method
import androidx.compose.material3.SwipeToDismissBoxState

fun main() {
    val clazz = Class.forName("androidx.compose.material3.SwipeToDismissBoxKt")
    for (method in clazz.declaredMethods) {
        if (method.name.contains("rememberSwipeToDismissBoxState")) {
            println("Method: ${method.name}")
            for (param in method.parameters) {
                println("  Param: ${param.name} type ${param.type}")
            }
        }
    }
}
