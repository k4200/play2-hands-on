package controllers

import play.api.mvc._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.db.slick._
import slick.driver.JdbcProfile
import models.Tables._
import javax.inject.Inject
import scala.concurrent.Future
import slick.driver.H2Driver.api._

import play.api.libs.json._ // Play2のJSONサポート機能を使用するために必要なimport文です
import play.api.libs.functional.syntax._

// Play2のJSONサポートではオブジェクトとJSONの返還を行うためにReadsやWritesでマッピングを定義する必要があります
// 先にobjectを書かないとコンパイルエラーになる
object JsonController {

  // ユーザー情報を受け取るためのケースクラス
  case class UserForm(id: Option[Long], name: String, companyId: Option[Int])

  // UsersRowをJSONに変換するためのWritesを定義
  implicit val usersRowWritesWrites = (
    (__ \ "id").write[Long] and
    (__ \ "name").write[String] and
    (__ \ "companyId").writeNullable[Int])(unlift(UsersRow.unapply))

  // JsonをUserFormに変換するためのReadsを定義
  // implicit val userFormFormat = (
  implicit val userFormReads = Json.reads[UserForm]
}

// テンプレートを使用していないので国際化機能のために必要だったMessagesApiのDIやI18nSupportトレイトのミックスインは行っていません。
class JsonController @Inject() (val dbConfigProvider: DatabaseConfigProvider) extends Controller
    with HasDatabaseConfigProvider[JdbcProfile] {

  // コンパニオンオブジェクトに定義したReads,Writesを参照するためにimport文を追加
  import JsonController._

  /**
   * 一覧表示
   */
  def list = Action.async { implicit rs =>
    // IDの昇順に全てのユーザー情報を取得
    db.run(Users.sortBy(t => t.id).result).map { users =>
      // ユーザーの一覧をJSONで返す
      Ok(Json.obj("users" -> users))
    }
  }

  /**
   * ユーザー登録
   */
  def create = Action.async(parse.json) { implicit rs =>
    rs.body.validate[UserForm].map { form =>
      // OKの場合はユーザーを登録
      val user = UsersRow(0, form.name, form.companyId)
      db.run(Users += user).map { _ =>
        Ok(Json.obj("result" -> "success"))
      }
    }.recoverTotal { e =>
      // NGの場合はバリデーションエラーを返す
      Future {
        BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e)))
      }
    }
  }

  /**
   * ユーザー更新
   */
  def update = Action.async(parse.json) { implicit rs => 
    rs.body.validate[UserForm].map { form => 
      // OKの場合はユーザー情報を更新
      val user = UsersRow(form.id.get, form.name, form.companyId)
      db.run(Users.filter(t => t.id === user.id.bind).update(user)).map { _ =>
        Ok(Json.obj("result" -> "success"))
      }
    }.recoverTotal { e =>
      // NGの場合はバリデーションエラーを返す
      Future {
        BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e)))
      }
    }
  }

  /**
   * ユーザー削除
   */
  def remove(id: Long) = Action.async { implicit rs =>
    // ユーザーを削除
    db.run(Users.filter(t => t.id === id.bind).delete).map { _ =>
      Ok(Json.obj("result" -> "success"))
    }
  }
}

