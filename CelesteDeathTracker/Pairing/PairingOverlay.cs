using Celeste;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Monocle;

namespace CelesteDeathTracker;

/// <summary>
/// Bandeau affiché par-dessus le jeu (menu comme niveau) tant que l'appairage n'est pas terminé.
/// C'est ce qui dispense de chercher une option dans les menus.
/// </summary>
internal sealed class PairingOverlay : DrawableGameComponent
{
    private static readonly TimeSpan PairedBannerDuration = TimeSpan.FromSeconds(6);
    private static readonly TimeSpan FailedBannerDuration = TimeSpan.FromSeconds(8);

    public PairingOverlay(Game game) : base(game)
    {
        DrawOrder = int.MaxValue; // par-dessus tout
    }

    public override void Draw(GameTime gameTime)
    {
        var view = PairingManager.View;
        string? title = null, line = null, code = null;
        var color = Color.White;
        var age = DateTime.UtcNow - view.At;

        switch (view.Phase)
        {
            case PairingPhase.Waiting:
                title = $"Death Tracker ({view.Server}) : associe ce jeu à ton compte";
                line = $"Sur {ShortUrl(view.Url)}, connecte-toi puis saisis ce code :";
                code = view.UserCode;
                break;
            case PairingPhase.Paired when age < PairedBannerDuration:
                title = $"Death Tracker ({view.Server}) : jeu associé à ton compte";
                color = Color.LightGreen;
                break;
            case PairingPhase.Failed when age < FailedBannerDuration:
                title = $"Death Tracker ({view.Server}) : {view.Message}";
                color = Color.Orange;
                break;
        }

        if (title is null)
            return;

        var height = code is null ? 70f : 200f;
        const float width = 1180f;
        var x = (1920f - width) / 2f;
        const float y = 20f;

        // "Monocle.Draw" : dans cette classe, "Draw" seul désignerait la méthode ci-dessus.
        Monocle.Draw.SpriteBatch.Begin(SpriteSortMode.Deferred, BlendState.AlphaBlend, SamplerState.LinearClamp,
            DepthStencilState.None, RasterizerState.CullNone, null, Engine.ScreenMatrix);

        Monocle.Draw.Rect(x, y, width, height, Color.Black * 0.8f);
        ActiveFont.DrawOutline(title, new Vector2(1920f / 2f, y + 18f), new Vector2(0.5f, 0f),
            Vector2.One * 0.6f, color, 2f, Color.Black);

        if (line is not null)
            ActiveFont.DrawOutline(line, new Vector2(1920f / 2f, y + 70f), new Vector2(0.5f, 0f),
                Vector2.One * 0.5f, Color.White, 2f, Color.Black);

        if (code is not null)
            ActiveFont.DrawOutline(code, new Vector2(1920f / 2f, y + 112f), new Vector2(0.5f, 0f),
                Vector2.One * 1.1f, Color.Gold, 3f, Color.Black);

        Monocle.Draw.SpriteBatch.End();
    }

    private static string ShortUrl(string url) =>
        url.Replace("https://", "", StringComparison.OrdinalIgnoreCase)
           .Replace("http://", "", StringComparison.OrdinalIgnoreCase);
}
