package cn.mce.starlight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mce.starlight.data.Boards
import cn.mce.starlight.data.ForumBoard

/** 沉浸式版块列表 */
@Composable
fun ForumBoardScreen(
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    onOpenBoard: (ForumBoard) -> Unit,
) {
    val grouped = remember { Boards.all.groupBy { it.group } }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + 56.dp,
                bottom = 120.dp,
            ),
        ) {
            grouped.forEach { (group, boards) ->
                item {
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp),
                    )
                }
                items(boards, key = { it.fid }) { board ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBoard(board) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Text(board.name, style = MaterialTheme.typography.bodyLarge)
                        board.description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 18.dp))
                }
            }
        }

        ImmersiveTopBar(
            title = "社区论坛",
            modifier = Modifier.align(Alignment.TopStart),
            onMenu = onMenu,
        )
    }
}
