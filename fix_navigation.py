with open('app/src/main/java/com/example/xdownloader/ui/screens/MainAppScreen.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(r"onClick = \{ navController\.navigate\(\"(.*?)\"\) \}",
                 r"""onClick = {
                        navController.navigate("\1") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }""", content)

with open('app/src/main/java/com/example/xdownloader/ui/screens/MainAppScreen.kt', 'w') as f:
    f.write(content)
