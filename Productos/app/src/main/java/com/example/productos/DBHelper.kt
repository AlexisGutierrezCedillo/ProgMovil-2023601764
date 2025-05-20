// DBHelper.kt
package com.example.productos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(context: Context) :
    SQLiteOpenHelper(context, "miBase.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE producto (
                id_producto INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                nombre      TEXT    NOT NULL,
                precio      DOUBLE  NOT NULL,
                descripcion TEXT,
                imagenURL   TEXT
            );
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL("DROP TABLE IF EXISTS producto")
        onCreate(db)
    }

    fun insertarProducto(producto: Producto) {
        writableDatabase.use { db ->
            val v = ContentValues().apply {
                put("nombre",      producto.nombre)
                put("precio",      producto.precio)
                put("descripcion", producto.descripcion)
                put("imagenURL",   producto.imagenUrl)
            }
            db.insert("producto", null, v)
        }
    }

    fun actualizarProducto(producto: Producto) {
        writableDatabase.use { db ->
            val v = ContentValues().apply {
                put("nombre",      producto.nombre)
                put("precio",      producto.precio)
                put("descripcion", producto.descripcion)
                put("imagenURL",   producto.imagenUrl)
            }
            db.update("producto", v, "id_producto=?", arrayOf(producto.id.toString()))
        }
    }

    fun eliminarProducto(id: Int) {
        writableDatabase.use { db ->
            db.delete("producto", "id_producto=?", arrayOf(id.toString()))
        }
    }
}
