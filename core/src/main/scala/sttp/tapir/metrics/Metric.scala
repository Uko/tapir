package sttp.tapir.metrics

import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.model.{ServerRequest, ServerResponse}

case class Metric[F[_], M](
    metric: M,
    /** Called when the request starts. */
    onRequest: (ServerRequest, M, MonadError[F]) => F[EndpointMetric[F]]
)

case class EndpointMetric[F[_]](
    /** Called when an endpoint matches the request, before calling the server logic. */
    onEndpointRequest: Option[Endpoint[_, _, _, _] => F[Unit]] = None,
    onResponse: Option[(Endpoint[_, _, _, _], ServerResponse[_]) => F[Unit]] = None,
    onException: Option[(Endpoint[_, _, _, _], Throwable) => F[Unit]] = None
) {
  def onEndpointRequest(f: Endpoint[_, _, _, _] => F[Unit]): EndpointMetric[F] = this.copy(onEndpointRequest = Some(f))
  def onResponse(f: (Endpoint[_, _, _, _], ServerResponse[_]) => F[Unit]): EndpointMetric[F] = this.copy(onResponse = Some(f))
  def onException(f: (Endpoint[_, _, _, _], Throwable) => F[Unit]): EndpointMetric[F] = this.copy(onException = Some(f))
}

case class MetricLabels(
    forRequest: Seq[(String, (Endpoint[_, _, _, _], ServerRequest) => String)],
    forResponse: Seq[(String, Either[Throwable, ServerResponse[_]] => String)]
) {
  def forRequestNames: Seq[String] = forRequest.map { case (name, _) => name }
  def forResponseNames: Seq[String] = forResponse.map { case (name, _) => name }
  def forRequest(ep: Endpoint[_, _, _, _], req: ServerRequest): Seq[String] = forRequest.map { case (_, f) => f(ep, req) }
  def forResponse(res: ServerResponse[_]): Seq[String] = forResponse.map { case (_, f) => f(Right(res)) }
  def forResponse(ex: Throwable): Seq[String] = forResponse.map { case (_, f) => f(Left(ex)) }
}

object MetricLabels {

  /** Labels request by path and method, response by status code */
  lazy val Default: MetricLabels = MetricLabels(
    forRequest = Seq(
      "path" -> { case (ep, _) => ep.renderPathTemplate(renderQueryParam = None) },
      "method" -> { case (_, req) => req.method.method }
    ),
    forResponse = Seq(
      "status" -> {
        case Right(r) =>
          r.code match {
            case c if c.isInformational => "1xx"
            case c if c.isSuccess       => "2xx"
            case c if c.isRedirect      => "3xx"
            case c if c.isClientError   => "4xx"
            case c if c.isServerError   => "5xx"
            case _                      => ""
          }
        case Left(_) => "5xx"
      }
    )
  )
}
