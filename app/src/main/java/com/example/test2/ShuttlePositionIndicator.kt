package com.example.test2
import com.example.test2.ModbusManager
import android.view.View
import android.widget.ImageView

class ShuttlePositionIndicator(
    private val posIcons: List<ImageView>,  // ivPos1..ivPos6
    private val onRes: List<Int>,
    private val offRes: List<Int>,
    private val shuttle: ImageView,
    private val belt: View
) {
    init {
        require(posIcons.size == 13 && onRes.size == 13 && offRes.size == 13) {
            "Cần đúng 13 icon và 13 drawable on/off"
        }
    }

    /**
     * pos: 1..6
     */
    fun update(pos: Int) {
        // 1) cập nhật trạng thái on/off các ô số
        posIcons.forEachIndexed { idx, iv ->
            iv.setImageResource(if (idx + 1 == pos) onRes[idx] else offRes[idx])
        }

        // 2) di chuyển shuttle
        if (pos in 1..posIcons.size) {
            val target = posIcons[pos - 1]
            // tính x tương đối trong belt:
            // lấy toạ độ của target so với belt
            val beltX    = belt.x
            val iconX    = target.x
            val offsetX  = (target.width - shuttle.width) / 2f
            // set trực tiếp hoặc animate
            shuttle.animate()
                .x(beltX + iconX + offsetX)
                .setDuration(150)
                .start()
        }
    }
}