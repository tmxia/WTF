package top.easelink.lcg.ui.main.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.easelink.lcg.R

/**
 * 列表页通用三态状态组件：加载中 / 空态 / 错误重试。
 * 供各 Compose 列表 Screen 复用，避免每页重复实现状态占位。
 */

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = Color(0xFFE53935),
            strokeWidth = 2.dp
        )
    }
}

@Composable
fun EmptyStateView(
    text: String = stringResource(R.string.empty_screen),
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color(0xFF999999),
            fontSize = 14.sp
        )
    }
}

@Composable
fun ErrorStateView(
    message: String = stringResource(R.string.error),
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = Color(0xFF999999),
            fontSize = 14.sp
        )
        Box(modifier = Modifier.padding(top = 12.dp)) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Text(text = stringResource(R.string.retry), color = Color.White)
            }
        }
    }
}
