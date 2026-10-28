package io.github.saksa84.francemapquiz.map

/**
 * Cadrage de la carte à l'écran : écran = carte × [scale] + décalage.
 * Objet immuable sans dépendance Android : chaque geste produit un nouveau cadrage, testable sur PC.
 */
data class MapViewport(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val minScale: Float,
    val maxScale: Float,
) {
    fun toMap(screenX: Float, screenY: Float) = MapPoint((screenX - offsetX) / scale, (screenY - offsetY) / scale)

    fun toScreen(point: MapPoint) = MapPoint(point.x * scale + offsetX, point.y * scale + offsetY)

    /** Zoom de [factor] autour d'un point de l'écran (les doigts), qui reste immobile sous les doigts. */
    fun zoomedAt(focusX: Float, focusY: Float, factor: Float): MapViewport {
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val anchor = toMap(focusX, focusY)
        return copy(scale = newScale, offsetX = focusX - anchor.x * newScale, offsetY = focusY - anchor.y * newScale)
    }

    fun pannedBy(dx: Float, dy: Float) = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    /** Empêche de perdre la carte : le centre de l'écran reste toujours au-dessus de la carte. */
    fun constrainedTo(bounds: MapBounds, viewWidth: Float, viewHeight: Float): MapViewport {
        val centerX = viewWidth / 2
        val centerY = viewHeight / 2
        return copy(
            offsetX = offsetX.coerceIn(centerX - bounds.maxX * scale, centerX - bounds.minX * scale),
            offsetY = offsetY.coerceIn(centerY - bounds.maxY * scale, centerY - bounds.minY * scale),
        )
    }

    companion object {
        /** Zoom maximal par rapport à la vue d'ensemble : assez pour bien voir Paris et la petite couronne. */
        const val MAX_ZOOM = 60f

        /**
         * Carte entière centrée dans la vue, avec une petite marge.
         * @param maxZoom zoom maximal par rapport à cette vue ([MapGeometry.maxZoom])
         */
        fun fit(bounds: MapBounds, viewWidth: Float, viewHeight: Float, margin: Float = 0.03f, maxZoom: Float = MAX_ZOOM): MapViewport {
            val scale = minOf(viewWidth / bounds.width, viewHeight / bounds.height) * (1 - 2 * margin)
            return MapViewport(
                scale = scale,
                offsetX = (viewWidth - bounds.width * scale) / 2 - bounds.minX * scale,
                offsetY = (viewHeight - bounds.height * scale) / 2 - bounds.minY * scale,
                minScale = scale,
                maxScale = scale * maxZoom,
            )
        }

        /**
         * Vue cadrée sur une partie de la carte ([focus] : la région d'un niveau), avec les mêmes limites
         * de zoom que la vue d'ensemble : on peut toujours dézoomer jusqu'à la carte entière.
         */
        fun focused(
            bounds: MapBounds,
            focus: MapBounds,
            viewWidth: Float,
            viewHeight: Float,
            margin: Float = 0.08f,
            maxZoom: Float = MAX_ZOOM,
        ): MapViewport {
            val overview = fit(bounds, viewWidth, viewHeight, maxZoom = maxZoom)
            if (focus.width <= 0f || focus.height <= 0f) return overview
            val scale = (minOf(viewWidth / focus.width, viewHeight / focus.height) * (1 - 2 * margin))
                .coerceIn(overview.minScale, overview.maxScale)
            return overview.copy(
                scale = scale,
                offsetX = viewWidth / 2 - (focus.minX + focus.maxX) / 2 * scale,
                offsetY = viewHeight / 2 - (focus.minY + focus.maxY) / 2 * scale,
            ).constrainedTo(bounds, viewWidth, viewHeight)
        }
    }
}
