with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'r') as f:
    content = f.read()

# Let's cleanly fix the end of the file.
content = content[:content.rfind("        } catch (e: Exception) {")] + """        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "網路錯誤")
        }
    }
}
"""

with open('app/src/main/java/com/example/xdownloader/api/XApiClient.kt', 'w') as f:
    f.write(content)
